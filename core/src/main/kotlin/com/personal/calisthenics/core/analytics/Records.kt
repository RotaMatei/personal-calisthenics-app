package com.personal.calisthenics.core.analytics

import com.personal.calisthenics.core.model.MovementCategory
import com.personal.calisthenics.core.model.WorkoutDay

/** One logged set (or one leg of a set), the input of every analytics function. */
data class LoggedSet(
    val sessionId: Long,
    val epochMs: Long,
    val exerciseId: String,
    val category: MovementCategory,
    val reps: Int?,
    val holdSeconds: Int?,
    val rir: Int?,
    val levelIndex: Int,
)

/** A finished session. */
data class CompletedSession(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val day: WorkoutDay,
    val deload: Boolean,
)

/** Post-workout soreness / stiffness rating, 1 (fine) to 5 (very stiff or sore). */
data class JointRating(val wrists: Int, val elbows: Int, val shoulders: Int) {
    init {
        require(wrists in 1..5 && elbows in 1..5 && shoulders in 1..5) { "Ratings are 1..5" }
    }
}
