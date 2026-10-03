package com.personal.calisthenics.core.analytics

enum class RecoveryState { NO_HISTORY, TOO_SOON, OPTIMAL_WINDOW, OVERDUE }

data class RecoveryStatus(
    val state: RecoveryState,
    val elapsedMs: Long,
    /** Fraction of the 72 h scale that has elapsed, 0..1. */
    val progress: Float,
    val headline: String,
    val detail: String,
    val hoursUntilWindow: Double,
    val hoursLeftInWindow: Double,
)

/** Tendon recovery timer: collagen synthesis peaks 48-72 hours after the last session. */
object Recovery {
    const val WINDOW_START_HOURS = 48
    const val WINDOW_END_HOURS = 72
    private const val HOUR_MS = 3_600_000L

    fun status(lastCompletedAtMs: Long?, nowMs: Long): RecoveryStatus {
        if (lastCompletedAtMs == null) {
            return RecoveryStatus(
                RecoveryState.NO_HISTORY, 0L, 0f,
                "No workouts logged yet",
                "Finish your first session to start the 48-72 hour tendon recovery clock.",
                0.0, 0.0,
            )
        }
        val elapsed = (nowMs - lastCompletedAtMs).coerceAtLeast(0L)
        val hours = elapsed.toDouble() / HOUR_MS
        val progress = (hours / WINDOW_END_HOURS).toFloat().coerceIn(0f, 1f)
        return when {
            hours < WINDOW_START_HOURS -> RecoveryStatus(
                RecoveryState.TOO_SOON, elapsed, progress,
                "Collagen synthesis in progress",
                "Tendons are still remodeling. The optimal window opens in ${formatHours(WINDOW_START_HOURS - hours)}; rest or walk today.",
                WINDOW_START_HOURS - hours, WINDOW_END_HOURS - WINDOW_START_HOURS.toDouble(),
            )
            hours <= WINDOW_END_HOURS -> RecoveryStatus(
                RecoveryState.OPTIMAL_WINDOW, elapsed, progress,
                "Optimal training window",
                "Collagen synthesis has peaked. Train now; the window closes in ${formatHours(WINDOW_END_HOURS - hours)}.",
                0.0, WINDOW_END_HOURS - hours,
            )
            else -> RecoveryStatus(
                RecoveryState.OVERDUE, elapsed, 1f,
                "Window passed - train today",
                "More than 72 hours since your last session. Warm up thoroughly and keep the first sets easy.",
                0.0, 0.0,
            )
        }
    }

    /** "1d 4h 12m" style label. */
    fun formatElapsed(elapsedMs: Long): String {
        val totalMinutes = elapsedMs / 60_000L
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private fun formatHours(hours: Double): String {
        val totalMinutes = Math.round(hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
