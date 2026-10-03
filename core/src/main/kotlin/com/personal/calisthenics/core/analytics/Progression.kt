package com.personal.calisthenics.core.analytics

import com.personal.calisthenics.core.model.Exercise
import com.personal.calisthenics.core.model.ExerciseKind

/** Persisted progression of one exercise: current ladder level plus the first three stage checkboxes. */
data class ProgressionState(val exerciseId: String, val levelIndex: Int, val stageMask: Int)

data class StageInfo(val number: Int, val title: String, val description: String)

/**
 * The 4-stage bodyweight progression gatekeeper. Stages 1-3 are checkboxes the user ticks; once all three are
 * ticked the app offers Stage 4, the leverage unlock to the next ladder level, which resets the checkboxes.
 */
object ProgressionGate {
    const val STAGE_1 = 1
    const val STAGE_2 = 2
    const val STAGE_3 = 4
    const val ALL_THREE = STAGE_1 or STAGE_2 or STAGE_3

    fun stages(exercise: Exercise, levelIndex: Int): List<StageInfo> {
        val next = exercise.ladder.getOrNull(levelIndex + 1)
        val stage4 = if (next != null) {
            "Advance to the next mechanical progression: ${exercise.ladder[levelIndex].name} -> ${next.name}."
        } else {
            "Top of the ladder: keep refining range, control and consistency."
        }
        return when (exercise.kind) {
            ExerciseKind.REPS -> listOf(
                StageInfo(1, "Volume Mastery", "Hit 3-4 sets x 12 clean reps with RIR 1-2."),
                StageInfo(2, "Eccentric Tempo", "Master a 4-second controlled lowering phase."),
                StageInfo(3, "Isometric Pause", "Hold a 2-second pause at the hardest mechanical point of the rep."),
                StageInfo(4, "Leverage Unlock", stage4),
            )
            ExerciseKind.HOLD -> listOf(
                StageInfo(1, "Volume Mastery", "Hit 3-4 sets x 20 seconds of clean holds with 1-2 seconds in reserve."),
                StageInfo(2, "Eccentric Tempo", "Master a 4-second controlled entry into and exit from the position."),
                StageInfo(3, "Isometric Pause", "Add a 2-second extra hold at the hardest point of the range."),
                StageInfo(4, "Leverage Unlock", stage4),
            )
        }
    }

    fun isDone(mask: Int, stage: Int): Boolean = when (stage) {
        1 -> mask and STAGE_1 != 0
        2 -> mask and STAGE_2 != 0
        3 -> mask and STAGE_3 != 0
        else -> false
    }

    fun toggle(mask: Int, stage: Int): Int = when (stage) {
        1 -> mask xor STAGE_1
        2 -> mask xor STAGE_2
        3 -> mask xor STAGE_3
        else -> mask
    }

    /** True when stages 1-3 are all ticked, so the unlock prompt can appear. */
    fun readyToUnlock(mask: Int): Boolean = mask and ALL_THREE == ALL_THREE

    fun hasNextLevel(exercise: Exercise, levelIndex: Int): Boolean = levelIndex < exercise.ladder.lastIndex

    /** Moves to the next ladder level and clears the checkboxes. No-op at the top of the ladder. */
    fun advance(exercise: Exercise, state: ProgressionState): ProgressionState =
        if (hasNextLevel(exercise, state.levelIndex)) state.copy(levelIndex = state.levelIndex + 1, stageMask = 0) else state

    fun initial(exercise: Exercise) = ProgressionState(exercise.id, exercise.startLevel, 0)

    /**
     * Hint for Stage 1: in one session, at least 3 sets of 12+ reps (or 20+ seconds for holds) at RIR 1-2.
     * It is only a suggestion; the checkbox stays under the user's control.
     */
    fun stage1Suggested(exercise: Exercise, sessionSets: List<LoggedSet>): Boolean {
        val qualifying = sessionSets.count { set ->
            when (exercise.kind) {
                ExerciseKind.REPS -> (set.reps ?: 0) >= 12 && (set.rir == null || set.rir in 1..2)
                ExerciseKind.HOLD -> (set.holdSeconds ?: 0) >= 20
            }
        }
        return qualifying >= 3
    }
}

data class ChartPoint(val sessionId: Long, val epochMs: Long, val value: Double)

/** Builds the per-session series for the Stats charts. */
object ChartSeries {
    /** Longest hold (seconds) of the given exercise in each session, oldest first. */
    fun maxHoldPerSession(sets: List<LoggedSet>, exerciseId: String): List<ChartPoint> =
        sets.filter { it.exerciseId == exerciseId && it.holdSeconds != null }
            .groupBy { it.sessionId }
            .map { (sessionId, group) -> ChartPoint(sessionId, group.minOf { it.epochMs }, group.maxOf { it.holdSeconds!! }.toDouble()) }
            .sortedBy { it.epochMs }

    /** Total clean reps of the given exercises in each session, oldest first. */
    fun totalRepsPerSession(sets: List<LoggedSet>, exerciseIds: Set<String>): List<ChartPoint> =
        sets.filter { it.exerciseId in exerciseIds && it.reps != null }
            .groupBy { it.sessionId }
            .map { (sessionId, group) -> ChartPoint(sessionId, group.minOf { it.epochMs }, group.sumOf { it.reps!! }.toDouble()) }
            .sortedBy { it.epochMs }
}

/** Y axis for a line chart: starts at zero and rounds the top up to a tidy step so the grid labels are round numbers. */
class LineScale private constructor(val max: Double, val ticks: List<Double>) {
    fun fraction(value: Double): Float = if (max <= 0.0) 0f else (value / max).toFloat().coerceIn(0f, 1f)

    companion object {
        fun of(values: List<Double>): LineScale {
            val peak = values.maxOrNull() ?: 0.0
            if (peak <= 0.0) return LineScale(1.0, listOf(0.0, 1.0))
            val step = niceStep(peak / 4.0)
            val top = Math.ceil(peak / step) * step
            val ticks = generateSequence(0.0) { it + step }.takeWhile { it <= top + 1e-9 }.toList()
            return LineScale(top, ticks)
        }

        private fun niceStep(raw: Double): Double {
            val exp = Math.floor(Math.log10(raw))
            val base = Math.pow(10.0, exp)
            val f = raw / base
            val nice = when {
                f <= 1.0 -> 1.0
                f <= 2.0 -> 2.0
                f <= 5.0 -> 5.0
                else -> 10.0
            }
            return nice * base
        }
    }
}
