package com.personal.calisthenics.core.timer

import com.personal.calisthenics.core.model.Tempo
import com.personal.calisthenics.core.plan.FlowItem

/** Factories for the four timer types of the workout player. */
object Timers {
    const val GET_READY_SEC = 5
    const val FLOW_TRANSITION_SEC = 10
    const val TEMPO_LEAD_IN_SEC = 3
    const val GET_READY_TEXT = "Get into position & lock scapula"

    private fun ms(seconds: Int): Long = seconds * 1000L

    private fun lastSecondsTicks(endMs: Long, startFloorMs: Long): List<TimerCue> =
        listOf(3000L, 2000L, 1000L)
            .map { TimerCue(endMs - it, CueType.COUNT_TICK) }
            .filter { it.atMs > startFloorMs }

    /**
     * Isometric hold: a 5 second "get ready" buffer followed immediately by the active hold.
     * The hold runs for [holdMaxSec] seconds; a soft double beep marks [holdMinSec] being reached.
     */
    fun isometric(holdMinSec: Int, holdMaxSec: Int, getReadySec: Int = GET_READY_SEC): TimelineTimer {
        require(holdMinSec in 1..holdMaxSec) { "Hold range must satisfy 1 <= min <= max" }
        val getReadyMs = ms(getReadySec)
        val holdMs = ms(holdMaxSec)
        val total = getReadyMs + holdMs
        val phases = listOf(
            TimerPhase(PhaseKind.GET_READY, "Get ready", getReadyMs, hint = GET_READY_TEXT),
            TimerPhase(PhaseKind.HOLD, "Hold", holdMs, hint = "Keep the elbows locked and the shoulders down"),
        )
        val cues = mutableListOf<TimerCue>()
        cues += TimerCue(0, CueType.GET_READY_START)
        cues += lastSecondsTicks(getReadyMs, 0L)
        cues += TimerCue(getReadyMs, CueType.HOLD_GO)
        val minAt = getReadyMs + ms(holdMinSec)
        if (holdMinSec < holdMaxSec) cues += TimerCue(minAt, CueType.HOLD_MIN_REACHED)
        // 3-2-1 ticks before the end of the hold, skipping any tick that would collide with the minimum cue.
        cues += lastSecondsTicks(total, getReadyMs).filter { holdMinSec == holdMaxSec || it.atMs != minAt }
        cues += TimerCue(total, CueType.HOLD_STOP)
        return TimelineTimer(phases, cues)
    }

    /** Rest countdown with a 10 second warning (for rests of 30 s or more) and a 3-2-1 tick. */
    fun rest(seconds: Int): TimelineTimer {
        require(seconds >= 1) { "Rest must be at least one second" }
        val total = ms(seconds)
        val phases = listOf(TimerPhase(PhaseKind.REST, "Rest", total, hint = "Breathe slowly and shake out the arms"))
        val cues = mutableListOf<TimerCue>()
        cues += TimerCue(0, CueType.REST_START)
        if (seconds >= 30) cues += TimerCue(total - 10_000, CueType.REST_WARNING)
        cues += lastSecondsTicks(total, 0L)
        cues += TimerCue(total, CueType.REST_DONE)
        return TimelineTimer(phases, cues)
    }

    /**
     * Tempo metronome: a 3-2-1 lead-in, then [reps] repetitions of lower / bottom pause / drive / top pause.
     * Each phase boundary emits its own beep + vibration so you can run the set eyes-free.
     */
    fun tempo(tempo: Tempo, reps: Int, leadInSec: Int = TEMPO_LEAD_IN_SEC): TimelineTimer {
        require(reps >= 1) { "At least one rep" }
        val phases = mutableListOf<TimerPhase>()
        val cues = mutableListOf<TimerCue>()
        var cursor = 0L

        val leadMs = ms(leadInSec)
        if (leadMs > 0) {
            phases += TimerPhase(PhaseKind.GET_READY, "Get set", leadMs, hint = "Tempo ${tempo.notation}")
            for (second in 0 until leadInSec) cues += TimerCue(ms(second), CueType.COUNT_TICK)
            cursor += leadMs
        }
        for (rep in 1..reps) {
            if (tempo.eccentricSec > 0) {
                phases += TimerPhase(PhaseKind.LOWER, "Lower", ms(tempo.eccentricSec), rep = rep, hint = "Slow and controlled")
                cues += TimerCue(cursor, CueType.TEMPO_LOWER)
                cursor += ms(tempo.eccentricSec)
            }
            if (tempo.bottomPauseSec > 0) {
                phases += TimerPhase(PhaseKind.PAUSE_BOTTOM, "Pause", ms(tempo.bottomPauseSec), rep = rep, hint = "Hold the bottom position")
                cues += TimerCue(cursor, CueType.TEMPO_PAUSE_BOTTOM)
                cursor += ms(tempo.bottomPauseSec)
            }
            if (tempo.concentricSec > 0) {
                val label = if (tempo.explosive) "Drive up!" else "Drive"
                phases += TimerPhase(PhaseKind.DRIVE, label, ms(tempo.concentricSec), rep = rep, hint = if (tempo.explosive) "Explosive" else "Smooth and strong")
                cues += TimerCue(cursor, CueType.TEMPO_DRIVE)
                cursor += ms(tempo.concentricSec)
            }
            if (tempo.topPauseSec > 0) {
                phases += TimerPhase(PhaseKind.PAUSE_TOP, "Squeeze", ms(tempo.topPauseSec), rep = rep, hint = "Squeeze at the top")
                cues += TimerCue(cursor, CueType.TEMPO_PAUSE_TOP)
                cursor += ms(tempo.topPauseSec)
            }
        }
        cues += TimerCue(cursor, CueType.SET_DONE)
        return TimelineTimer(phases, cues)
    }

    /**
     * Flow timer for warm-up and stretching: every drill is preceded by a 10 second setup transition and
     * the timer rolls straight into the next drill without any taps.
     */
    fun flow(items: List<FlowItem>, transitionSec: Int = FLOW_TRANSITION_SEC): TimelineTimer {
        require(items.isNotEmpty()) { "A flow needs at least one drill" }
        val phases = mutableListOf<TimerPhase>()
        val cues = mutableListOf<TimerCue>()
        var cursor = 0L
        items.forEachIndexed { index, item ->
            val transitionMs = ms(transitionSec)
            if (transitionMs > 0) {
                phases += TimerPhase(
                    PhaseKind.TRANSITION, "Get ready: ${item.label}", transitionMs,
                    itemIndex = index, hint = item.instruction,
                )
                cues += TimerCue(cursor, CueType.FLOW_TRANSITION)
                cues += lastSecondsTicks(cursor + transitionMs, cursor)
                cursor += transitionMs
            }
            val drillMs = ms(item.seconds)
            phases += TimerPhase(
                PhaseKind.DRILL, item.label, drillMs,
                itemIndex = index, subLabels = item.subLabels, hint = item.instruction,
            )
            cues += TimerCue(cursor, CueType.FLOW_DRILL_START)
            if (item.subLabels.size > 1) {
                val slice = drillMs / item.subLabels.size
                for (k in 1 until item.subLabels.size) cues += TimerCue(cursor + slice * k, CueType.FLOW_SUBSTEP)
            }
            if (item.seconds >= 10) cues += lastSecondsTicks(cursor + drillMs, cursor)
            cursor += drillMs
        }
        cues += TimerCue(cursor, CueType.FLOW_DONE)
        return TimelineTimer(phases, cues)
    }
}
