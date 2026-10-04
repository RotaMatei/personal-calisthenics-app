package com.personal.calisthenicsguide.data

import com.personal.calisthenics.core.analytics.CompletedSession
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.analytics.LoggedSet
import com.personal.calisthenics.core.analytics.ProgressionGate
import com.personal.calisthenics.core.analytics.ProgressionState
import com.personal.calisthenics.core.model.MovementCategory
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenics.core.seed.SeedData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Setting keys stored in the key/value table. */
object SettingKeys {
    const val BLOCK_START = "block_start"
    const val COLD_MODE = "cold_mode"
    const val DAY_OVERRIDE = "day_override"
    const val SOUND_ON = "sound_on"
    const val VIBRATION_ON = "vibration_on"
}

/** A finished session plus its joint ratings (when logged). */
data class SessionRecord(
    val session: CompletedSession,
    val rating: JointRating?,
    val coldMode: Boolean,
)

/** Thin layer between Room and the pure `:core` types. */
class Repository(private val db: AppDatabase) {

    // ---------------------------------------------------------------- sessions

    val completedSessions: Flow<List<SessionRecord>> = db.sessions().completed().map { list -> list.mapNotNull { it.toRecord() } }

    suspend fun startSession(day: WorkoutDay, deload: Boolean, coldMode: Boolean, nowMs: Long): Long =
        db.sessions().insert(SessionEntity(startedAtMs = nowMs, day = day.label, deload = deload, coldMode = coldMode))

    suspend fun finishSession(sessionId: Long, rating: JointRating?, nowMs: Long) {
        val session = db.sessions().byId(sessionId) ?: return
        db.sessions().update(
            session.copy(
                endedAtMs = nowMs,
                wrists = rating?.wrists,
                elbows = rating?.elbows,
                shoulders = rating?.shoulders,
            ),
        )
        // A manually picked day applies to this one session; the next one goes back to the A -> B -> C rotation.
        db.settings().put(SettingEntity(SettingKeys.DAY_OVERRIDE, ""))
    }

    /** Drops a session that was started but never produced a set (for example when the user cancels at once). */
    suspend fun discardIfEmpty(sessionId: Long) {
        if (db.setLogs().forSession(sessionId).isEmpty()) db.sessions().delete(sessionId)
    }

    suspend fun cleanUpAbandoned() = db.sessions().deleteEmptyUnfinished()

    // ---------------------------------------------------------------- sets

    val loggedSets: Flow<List<LoggedSet>> = db.setLogs().allCompleted().map { list -> list.map { it.toCore() } }

    suspend fun logSet(
        sessionId: Long,
        nowMs: Long,
        exerciseId: String,
        category: MovementCategory,
        stepId: String,
        setNumber: Int,
        side: String?,
        reps: Int?,
        holdSeconds: Int?,
        rir: Int?,
        levelIndex: Int,
    ): Long = db.setLogs().insert(
        SetLogEntity(
            sessionId = sessionId, epochMs = nowMs, exerciseId = exerciseId, category = category.name, stepId = stepId,
            setNumber = setNumber, side = side, reps = reps, holdSeconds = holdSeconds, rir = rir, levelIndex = levelIndex,
        ),
    )

    suspend fun deleteSet(id: Long) = db.setLogs().delete(id)

    // ---------------------------------------------------------------- progression

    val progression: Flow<Map<String, ProgressionState>> = db.progression().all().map { list ->
        list.associate { it.exerciseId to ProgressionState(it.exerciseId, it.levelIndex, it.stageMask) }
    }

    /** Current state of [exerciseId]; falls back to the seeded start level. */
    suspend fun progressionOf(exerciseId: String): ProgressionState {
        val row = db.progression().get(exerciseId)
        return if (row != null) ProgressionState(row.exerciseId, row.levelIndex, row.stageMask)
        else ProgressionGate.initial(SeedData.exercise(exerciseId))
    }

    suspend fun saveProgression(state: ProgressionState) =
        db.progression().upsert(ProgressionEntity(state.exerciseId, state.levelIndex, state.stageMask))

    // ---------------------------------------------------------------- settings

    val settings: Flow<Map<String, String>> = db.settings().all().map { list -> list.associate { it.key to it.value } }

    suspend fun setting(key: String): String? = db.settings().get(key)

    suspend fun putSetting(key: String, value: String) = db.settings().put(SettingEntity(key, value))

    /** The first Monday of the 6-week block; created on first launch so the deload tracker has an anchor. */
    suspend fun ensureBlockStart(today: LocalDate): LocalDate {
        val existing = db.settings().get(SettingKeys.BLOCK_START)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (existing != null) return existing
        val start = Deload.normalizeStart(today)
        db.settings().put(SettingEntity(SettingKeys.BLOCK_START, start.toString()))
        return start
    }

    // ---------------------------------------------------------------- checklist and media

    fun checklistFor(date: LocalDate): Flow<Map<String, Boolean>> =
        db.checklist().forDate(date.toString()).map { rows -> rows.associate { it.itemId to it.done } }

    suspend fun setChecklist(date: LocalDate, itemId: String, done: Boolean) =
        db.checklist().put(ChecklistEntity(date.toString(), itemId, done))

    val mediaOverrides: Flow<Map<String, MediaOverrideEntity>> = db.media().all().map { rows -> rows.associateBy { it.exerciseId } }

    suspend fun setMedia(exerciseId: String, uri: String, mimeType: String) = db.media().put(MediaOverrideEntity(exerciseId, uri, mimeType))

    suspend fun clearMedia(exerciseId: String) = db.media().delete(exerciseId)

    // ---------------------------------------------------------------- mapping

    private fun SessionEntity.toRecord(): SessionRecord? {
        val end = endedAtMs ?: return null
        val day = WorkoutDay.fromLabel(day) ?: return null
        val rating = if (wrists != null && elbows != null && shoulders != null) JointRating(wrists, elbows, shoulders) else null
        return SessionRecord(CompletedSession(id, startedAtMs, end, day, deload), rating, coldMode)
    }

    private fun SetLogEntity.toCore() = LoggedSet(
        sessionId = sessionId,
        epochMs = epochMs,
        exerciseId = exerciseId,
        category = runCatching { MovementCategory.valueOf(category) }.getOrDefault(MovementCategory.CORE),
        reps = reps,
        holdSeconds = holdSeconds,
        rir = rir,
        levelIndex = levelIndex,
    )
}
