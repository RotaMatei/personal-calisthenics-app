package com.personal.calisthenics.core.timer

enum class PhaseKind {
    GET_READY,
    HOLD,
    LOWER,
    PAUSE_BOTTOM,
    DRIVE,
    PAUSE_TOP,
    REST,
    TRANSITION,
    DRILL,
}

/**
 * One segment of a timeline. [rep] is the 1-based repetition for tempo timers, [itemIndex] the drill
 * index for flow timers, [subLabels] split a drill evenly into named sub-steps.
 */
data class TimerPhase(
    val kind: PhaseKind,
    val label: String,
    val durationMs: Long,
    val rep: Int? = null,
    val itemIndex: Int? = null,
    val subLabels: List<String> = emptyList(),
    val hint: String = "",
)

/** A cue fires when the timeline's elapsed time passes [atMs]. */
data class TimerCue(val atMs: Long, val type: CueType)

data class TimerSnapshot(
    val phaseIndex: Int,
    val phase: TimerPhase?,
    val phaseElapsedMs: Long,
    val phaseRemainingMs: Long,
    val totalElapsedMs: Long,
    val totalRemainingMs: Long,
    val totalMs: Long,
    val running: Boolean,
    val finished: Boolean,
    val idle: Boolean,
) {
    /** 0..1 progress inside the current phase. */
    val phaseProgress: Float
        get() = if (phase == null || phase.durationMs <= 0L) 1f else (phaseElapsedMs.toFloat() / phase.durationMs).coerceIn(0f, 1f)

    val totalProgress: Float
        get() = if (totalMs <= 0L) 1f else (totalElapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)

    /** Index of the active sub-label for the current phase, or null if the phase has none. */
    val subLabelIndex: Int?
        get() {
            val p = phase ?: return null
            if (p.subLabels.isEmpty() || p.durationMs <= 0L) return null
            val slice = p.durationMs.toFloat() / p.subLabels.size
            return (phaseElapsedMs / slice).toInt().coerceIn(0, p.subLabels.size - 1)
        }

    /** Whole seconds remaining in the phase, rounded up (what a countdown display shows). */
    val phaseRemainingSeconds: Int get() = ((phaseRemainingMs + 999L) / 1000L).toInt()
}

/**
 * A deterministic, clock-injected timer. All timers (isometric get-ready + hold, tempo metronome, rest, flow)
 * are expressed as an ordered list of phases plus cues at absolute offsets. The caller passes the current
 * monotonic time (for example SystemClock.elapsedRealtime) so the timer never drifts or depends on tick rate.
 */
class TimelineTimer(
    val phases: List<TimerPhase>,
    cues: List<TimerCue>,
) {
    private val sortedCues: List<TimerCue> = cues.sortedBy { it.atMs }
    private val phaseStarts: LongArray
    val totalMs: Long

    private var accumulatedMs: Long = 0L
    private var runningSince: Long? = null
    private var started = false
    private var lastPolledElapsed: Long = -1L
    private var finishedFlag = false

    init {
        var cursor = 0L
        phaseStarts = LongArray(phases.size) { index ->
            val start = cursor
            cursor += phases[index].durationMs
            start
        }
        totalMs = cursor
    }

    val isRunning: Boolean get() = runningSince != null
    val isFinished: Boolean get() = finishedFlag
    val isStarted: Boolean get() = started

    fun start(nowMs: Long) {
        accumulatedMs = 0L
        runningSince = nowMs
        started = true
        finishedFlag = false
        lastPolledElapsed = -1L
    }

    fun pause(nowMs: Long) {
        val since = runningSince ?: return
        accumulatedMs += nowMs - since
        runningSince = null
    }

    fun resume(nowMs: Long) {
        if (!started || finishedFlag || runningSince != null) return
        runningSince = nowMs
    }

    fun elapsedMs(nowMs: Long): Long {
        val since = runningSince
        val raw = accumulatedMs + if (since != null) nowMs - since else 0L
        return raw.coerceIn(0L, totalMs)
    }

    /** Index of the phase containing [elapsed]; the last phase once the timeline is complete. */
    fun phaseIndexAt(elapsed: Long): Int {
        if (phases.isEmpty()) return -1
        if (elapsed >= totalMs) return phases.lastIndex
        var index = 0
        for (i in phases.indices) {
            if (elapsed >= phaseStarts[i]) index = i else break
        }
        return index
    }

    fun snapshot(nowMs: Long): TimerSnapshot {
        val elapsed = elapsedMs(nowMs)
        val done = finishedFlag || (started && elapsed >= totalMs && totalMs >= 0)
        val index = phaseIndexAt(elapsed)
        val phase = phases.getOrNull(index)
        val phaseElapsed = if (phase == null) 0L else (elapsed - phaseStarts[index]).coerceIn(0L, phase.durationMs)
        val phaseRemaining = if (phase == null) 0L else phase.durationMs - phaseElapsed
        return TimerSnapshot(
            phaseIndex = index,
            phase = phase,
            phaseElapsedMs = phaseElapsed,
            phaseRemainingMs = phaseRemaining,
            totalElapsedMs = elapsed,
            totalRemainingMs = totalMs - elapsed,
            totalMs = totalMs,
            running = isRunning,
            finished = done,
            idle = !started,
        )
    }

    /**
     * Returns the cues crossed since the previous call (in order) and marks the timer finished
     * once the whole timeline has elapsed. Safe to call as often as you like.
     */
    fun poll(nowMs: Long): List<TimerCue> {
        if (!started) return emptyList()
        val elapsed = elapsedMs(nowMs)
        val fired = sortedCues.filter { it.atMs > lastPolledElapsed && it.atMs <= elapsed }
        lastPolledElapsed = elapsed
        if (elapsed >= totalMs && !finishedFlag) {
            finishedFlag = true
            if (runningSince != null) {
                accumulatedMs = totalMs
                runningSince = null
            }
        }
        return fired
    }

    /** Jumps to the start of the next phase (used by "skip" on flow timers). Cues in between are dropped. */
    fun skipToNextPhase(nowMs: Long) {
        if (!started || finishedFlag) return
        val elapsed = elapsedMs(nowMs)
        val index = phaseIndexAt(elapsed)
        val target = if (index + 1 < phases.size) phaseStarts[index + 1] else totalMs
        seek(target, nowMs)
    }

    /** Jumps back to the start of the current phase, or the previous one if less than 2 s into it. */
    fun skipToPreviousPhase(nowMs: Long) {
        if (!started) return
        val elapsed = elapsedMs(nowMs)
        val index = phaseIndexAt(elapsed)
        val intoPhase = elapsed - phaseStarts[index]
        val target = if (intoPhase > 2000L || index == 0) phaseStarts[index] else phaseStarts[index - 1]
        seek(target, nowMs)
    }

    /** Ends the timer immediately, firing nothing. */
    fun finishNow(nowMs: Long) {
        if (!started) return
        seek(totalMs, nowMs)
    }

    private fun seek(targetElapsed: Long, nowMs: Long) {
        val wasRunning = runningSince != null
        accumulatedMs = targetElapsed.coerceIn(0L, totalMs)
        runningSince = if (wasRunning) nowMs else null
        // Cues strictly before the target are consumed; a cue exactly at the target (phase start) still fires.
        lastPolledElapsed = accumulatedMs - 1L
        finishedFlag = false
    }
}
