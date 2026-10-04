package com.personal.calisthenicsguide.session

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.personal.calisthenics.core.analytics.JointAdvice
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.analytics.ProgressionState
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenics.core.session.EngineSnapshot
import com.personal.calisthenics.core.session.SessionEngine
import com.personal.calisthenics.core.session.SetResult
import com.personal.calisthenics.core.session.StageKind
import com.personal.calisthenics.core.session.newSessionEngine
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.data.SettingKeys
import com.personal.calisthenicsguide.feedback.Feedback
import com.personal.calisthenicsguide.service.WorkoutService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the finished-session screen shows. */
data class SessionSummary(
    val day: WorkoutDay,
    val deload: Boolean,
    val sets: Int,
    val minutes: Int,
    val messages: List<String>,
)

/** UI state of the Workout tab while a session exists. */
data class SessionUi(
    val active: Boolean = false,
    val day: WorkoutDay? = null,
    val snapshot: EngineSnapshot? = null,
    /** Progression ladder level suggested for the set being logged. */
    val defaultLevel: Int = 0,
    val summary: SessionSummary? = null,
)

/**
 * Owns the running workout: the [SessionEngine], persistence of logged sets, sound / vibration cues and the
 * foreground service that keeps everything alive with the screen off. Lives in the Application so a session
 * survives the activity being recreated or sent to the background.
 */
class SessionController(
    private val app: Application,
    private val repository: Repository,
    val feedback: Feedback,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    private var engine: SessionEngine? = null
    private var sessionId: Long = 0L
    private var startedWallMs: Long = 0L
    private var tickJob: Job? = null
    private var progression: Map<String, ProgressionState> = emptyMap()
    private var options: PlanOptions? = null

    init {
        scope.launch { repository.progression.collect { progression = it } }
        scope.launch { repository.settings.collect { feedback.beeper.enabled = it[SettingKeys.SOUND_ON] != "0"; feedback.haptics.enabled = it[SettingKeys.VIBRATION_ON] != "0" } }
    }

    val isActive: Boolean get() = engine != null

    private fun now(): Long = SystemClock.elapsedRealtime()

    // ------------------------------------------------------------------ lifecycle

    fun start(planOptions: PlanOptions) {
        if (engine != null) return
        options = planOptions
        val e = newSessionEngine(planOptions)
        engine = e
        startedWallMs = System.currentTimeMillis()
        scope.launch {
            repository.cleanUpAbandoned()
            sessionId = repository.startSession(planOptions.day, planOptions.deload, planOptions.coldMode, startedWallMs)
        }
        e.begin(now())
        ContextCompat.startForegroundService(app, Intent(app, WorkoutService::class.java))
        publish()
        startLoop()
    }

    /** Dismisses the finished-session summary and returns to the idle Workout tab. */
    fun dismissSummary() {
        _ui.value = SessionUi()
    }

    private fun startLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                val e = engine ?: break
                val cues = e.tick(now())
                // A burst of cues (for example after the phone slept) is played as its last cue only.
                cues.lastOrNull()?.let { feedback.cue(it) }
                publish()
                delay(100)
            }
        }
    }

    private fun publish() {
        val e = engine ?: return
        val snap = e.snapshot(now())
        val item = snap.item
        val level = if (item != null) levelFor(item.exerciseId) else 0
        _ui.value = SessionUi(active = true, day = options?.day, snapshot = snap, defaultLevel = level)
    }

    private fun levelFor(exerciseId: String): Int =
        progression[exerciseId]?.levelIndex ?: SeedData.exerciseOrNull(exerciseId)?.startLevel ?: 0

    // ------------------------------------------------------------------ actions (main thread)

    fun startFlow() = act { it.startFlow(now()) }
    fun startSet() = act { it.startSet(now()) }
    fun stopSet() = act { it.stopSet(now()) }
    fun skipSet() = act { it.skipSet(now()) }
    fun skipRest() = act { it.skipRest(now()) }
    fun extendRest(seconds: Int) = act { it.extendRest(seconds, now()) }
    fun pause() = act { it.pause(now()) }
    fun resume() = act { it.resume(now()) }
    fun skipFlowPhase() = act { it.skipFlowPhase(now()) }
    fun previousFlowPhase() = act { it.previousFlowPhase(now()) }
    fun endEarly() = act { it.endEarly() }

    fun setColdMode(on: Boolean) {
        act { it.setColdMode(on) }
        scope.launch { repository.putSetting(SettingKeys.COLD_MODE, if (on) "1" else "0") }
    }

    private fun act(block: (SessionEngine) -> Unit) {
        val e = engine ?: return
        block(e)
        e.tick(now()).lastOrNull()?.let { feedback.cue(it) }
        publish()
    }

    fun logSet(reps: Int?, holdSeconds: Int?, rir: Int?, levelIndex: Int) {
        val e = engine ?: return
        val item = e.snapshot(now()).item ?: return
        val result = SetResult(reps, holdSeconds, rir, levelIndex)
        val exercise = SeedData.exercise(item.exerciseId)
        val sid = sessionId
        val wall = System.currentTimeMillis()
        scope.launch {
            repository.logSet(
                sessionId = sid, nowMs = wall, exerciseId = item.exerciseId, category = exercise.category, stepId = item.stepId,
                setNumber = item.setNumber, side = item.side?.name, reps = reps, holdSeconds = holdSeconds, rir = rir, levelIndex = levelIndex,
            )
            val stored = repository.progressionOf(item.exerciseId)
            if (stored.levelIndex != levelIndex) repository.saveProgression(ProgressionState(item.exerciseId, levelIndex, 0))
        }
        act { it.logSet(result, now()) }
    }

    /** Saves the joint ratings (or none) and closes the session. */
    fun finish(rating: JointRating?) {
        val e = engine ?: return
        e.finish(rating)
        val entries = e.entries
        val endWall = System.currentTimeMillis()
        val sid = sessionId
        val day = options?.day ?: WorkoutDay.A
        val deload = options?.deload ?: false
        scope.launch {
            if (entries.isEmpty()) repository.discardIfEmpty(sid) else repository.finishSession(sid, rating, endWall)
        }
        val messages = JointAdvice.evaluate(rating).messages
        val minutes = ((endWall - startedWallMs) / 60_000L).toInt().coerceAtLeast(1)
        tickJob?.cancel()
        engine = null
        options = null
        app.stopService(Intent(app, WorkoutService::class.java))
        _ui.value = SessionUi(active = false, summary = SessionSummary(day, deload, entries.size, minutes, messages))
    }
}
