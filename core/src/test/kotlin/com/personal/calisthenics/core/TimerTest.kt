package com.personal.calisthenics.core

import com.personal.calisthenics.core.model.Tempo
import com.personal.calisthenics.core.plan.FlowItem
import com.personal.calisthenics.core.timer.CueCatalog
import com.personal.calisthenics.core.timer.CueType
import com.personal.calisthenics.core.timer.PhaseKind
import com.personal.calisthenics.core.timer.Timers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerTest {

    private fun types(cues: List<com.personal.calisthenics.core.timer.TimerCue>) = cues.map { it.type }

    @Test
    fun isometricHasFiveSecondGetReadyThenHold() {
        val timer = Timers.isometric(15, 20)
        assertEquals(25_000L, timer.totalMs)
        assertEquals(PhaseKind.GET_READY, timer.phases[0].kind)
        assertEquals(5_000L, timer.phases[0].durationMs)
        assertEquals("Get into position & lock scapula", timer.phases[0].hint)
        assertEquals(PhaseKind.HOLD, timer.phases[1].kind)
        assertEquals(20_000L, timer.phases[1].durationMs)
    }

    @Test
    fun isometricCueSequence() {
        val timer = Timers.isometric(15, 20)
        val t0 = 1_000L
        timer.start(t0)
        assertEquals(listOf(CueType.GET_READY_START), types(timer.poll(t0)))
        // Through the end of get-ready: three ticks, then GO exactly when the hold starts.
        assertEquals(
            listOf(CueType.COUNT_TICK, CueType.COUNT_TICK, CueType.COUNT_TICK, CueType.HOLD_GO),
            types(timer.poll(t0 + 5_000)),
        )
        // 15 s into the hold the minimum is reached.
        assertEquals(listOf(CueType.HOLD_MIN_REACHED), types(timer.poll(t0 + 20_000)))
        // Last three seconds tick, then STOP at the end, and the timer reports finished.
        assertEquals(
            listOf(CueType.COUNT_TICK, CueType.COUNT_TICK, CueType.COUNT_TICK, CueType.HOLD_STOP),
            types(timer.poll(t0 + 25_000)),
        )
        assertTrue(timer.isFinished)
        assertTrue(timer.poll(t0 + 40_000).isEmpty())
    }

    @Test
    fun isometricWithEqualMinAndMaxStillTicksBeforeTheEnd() {
        val timer = Timers.isometric(20, 20)
        timer.start(0)
        timer.poll(5_000)
        assertEquals(
            listOf(CueType.COUNT_TICK, CueType.COUNT_TICK, CueType.COUNT_TICK, CueType.HOLD_STOP),
            types(timer.poll(25_000)),
        )
    }

    @Test
    fun snapshotsTrackPhases() {
        val timer = Timers.isometric(15, 20)
        assertTrue(timer.snapshot(0).idle)
        timer.start(0)
        val early = timer.snapshot(2_000)
        assertEquals(PhaseKind.GET_READY, early.phase!!.kind)
        assertEquals(3, early.phaseRemainingSeconds)
        val hold = timer.snapshot(8_000)
        assertEquals(PhaseKind.HOLD, hold.phase!!.kind)
        assertEquals(3_000L, hold.phaseElapsedMs)
        assertEquals(17_000L, hold.phaseRemainingMs)
        assertFalse(hold.finished)
        timer.poll(30_000)
        assertTrue(timer.snapshot(30_000).finished)
    }

    @Test
    fun pauseAndResumeDoNotCountPausedTime() {
        val timer = Timers.rest(60)
        timer.start(0)
        timer.pause(10_000)
        assertEquals(10_000L, timer.elapsedMs(50_000))
        timer.resume(50_000)
        assertEquals(15_000L, timer.elapsedMs(55_000))
        assertTrue(timer.isRunning)
    }

    @Test
    fun restTimerCues() {
        val rest = Timers.rest(90)
        rest.start(0)
        val all = rest.poll(90_000)
        assertEquals(CueType.REST_START, all.first().type)
        assertEquals(CueType.REST_DONE, all.last().type)
        assertEquals(1, all.count { it.type == CueType.REST_WARNING })
        assertEquals(80_000L, all.first { it.type == CueType.REST_WARNING }.atMs)
        assertEquals(listOf(87_000L, 88_000L, 89_000L), all.filter { it.type == CueType.COUNT_TICK }.map { it.atMs })
        assertTrue(rest.isFinished)
    }

    @Test
    fun shortRestSkipsTheWarning() {
        val rest = Timers.rest(20)
        rest.start(0)
        assertEquals(0, rest.poll(20_000).count { it.type == CueType.REST_WARNING })
    }

    @Test
    fun tempoMetronomeRunsRepsInOrder() {
        val tempo = Tempo.parse("3-1-X-0")
        val timer = Timers.tempo(tempo, reps = 2)
        // 3 s lead-in + 2 reps x (3 + 1 + 1) = 13 s
        assertEquals(13_000L, timer.totalMs)
        assertEquals(1 + 3 * 2, timer.phases.size)
        timer.start(0)
        assertEquals(PhaseKind.GET_READY, timer.snapshot(1_000).phase!!.kind)
        val lower1 = timer.snapshot(4_000)
        assertEquals(PhaseKind.LOWER, lower1.phase!!.kind)
        assertEquals(1, lower1.phase!!.rep)
        assertEquals(PhaseKind.PAUSE_BOTTOM, timer.snapshot(6_500).phase!!.kind)
        assertEquals(PhaseKind.DRIVE, timer.snapshot(7_500).phase!!.kind)
        val rep2 = timer.snapshot(9_000)
        assertEquals(PhaseKind.LOWER, rep2.phase!!.kind)
        assertEquals(2, rep2.phase!!.rep)

        val cues = timer.poll(13_000)
        assertEquals(3, cues.count { it.type == CueType.COUNT_TICK })
        assertEquals(2, cues.count { it.type == CueType.TEMPO_LOWER })
        assertEquals(2, cues.count { it.type == CueType.TEMPO_PAUSE_BOTTOM })
        assertEquals(2, cues.count { it.type == CueType.TEMPO_DRIVE })
        assertEquals(CueType.SET_DONE, cues.last().type)
    }

    @Test
    fun tempoWithTopPauseAddsSqueezePhase() {
        val timer = Timers.tempo(Tempo.parse("2-0-1-1"), reps = 3, leadInSec = 0)
        assertEquals(3 * 4_000L, timer.totalMs)
        assertEquals(PhaseKind.LOWER, timer.phases[0].kind)
        assertEquals(PhaseKind.DRIVE, timer.phases[1].kind)
        assertEquals(PhaseKind.PAUSE_TOP, timer.phases[2].kind)
        assertEquals(9, timer.phases.size)
    }

    @Test
    fun flowAddsTenSecondTransitionBeforeEveryDrill() {
        val items = listOf(
            FlowItem("a", "x", "Dead hang", 15, "Hang", emptyList()),
            FlowItem("b", "y", "Squat", 45, "Squat", listOf("Left", "Right", "Both")),
        )
        val timer = Timers.flow(items)
        assertEquals((10 + 15 + 10 + 45) * 1000L, timer.totalMs)
        assertEquals(
            listOf(PhaseKind.TRANSITION, PhaseKind.DRILL, PhaseKind.TRANSITION, PhaseKind.DRILL),
            timer.phases.map { it.kind },
        )
        assertEquals(10_000L, timer.phases[0].durationMs)
        timer.start(0)
        val all = timer.poll(timer.totalMs)
        assertEquals(2, all.count { it.type == CueType.FLOW_TRANSITION })
        assertEquals(2, all.count { it.type == CueType.FLOW_DRILL_START })
        assertEquals(2, all.count { it.type == CueType.FLOW_SUBSTEP })
        assertEquals(CueType.FLOW_DONE, all.last().type)
    }

    @Test
    fun flowSubLabelsAdvanceEvenly() {
        val items = listOf(FlowItem("a", "x", "Circles", 50, "", listOf("Neck", "Shoulders", "Elbows", "Hips", "Ankles")))
        val timer = Timers.flow(items)
        timer.start(0)
        assertEquals(0, timer.snapshot(10_500).subLabelIndex)
        assertEquals(1, timer.snapshot(10_000 + 12_000).subLabelIndex)
        assertEquals(4, timer.snapshot(10_000 + 49_000).subLabelIndex)
    }

    @Test
    fun skipMovesToTheNextPhaseAndDropsSkippedCues() {
        val items = listOf(
            FlowItem("a", "x", "One", 30, "", emptyList()),
            FlowItem("b", "y", "Two", 30, "", emptyList()),
        )
        val timer = Timers.flow(items)
        timer.start(0)
        timer.poll(0)
        timer.skipToNextPhase(5_000) // from transition into drill one
        val fired = timer.poll(5_000)
        assertEquals(listOf(CueType.FLOW_DRILL_START), types(fired))
        assertEquals(PhaseKind.DRILL, timer.snapshot(5_000).phase!!.kind)
        assertEquals(10_000L, timer.elapsedMs(5_000))
        timer.skipToPreviousPhase(8_000) // 3 s into the drill, goes back to the start of the drill
        assertEquals(10_000L, timer.elapsedMs(8_000))
    }

    @Test
    fun finishNowEndsTheTimer() {
        val timer = Timers.isometric(15, 20)
        timer.start(0)
        timer.poll(0)
        timer.finishNow(3_000)
        val cues = timer.poll(3_000)
        assertEquals(listOf(CueType.HOLD_STOP), types(cues))
        assertTrue(timer.isFinished)
    }

    @Test
    fun everyCueHasABeepAndAVibrationAndAllVibrationsAreDistinct() {
        val signatures = mutableSetOf<String>()
        for (type in CueType.entries) {
            val beep = CueCatalog.beep(type)
            assertTrue("$type beep", beep.tones.isNotEmpty() && beep.totalMs in 1..1500)
            val haptic = CueCatalog.haptic(type)
            assertNotNull(haptic)
            assertEquals(haptic.timingsMs.size, haptic.fullAmplitudes().size)
            assertTrue("$type distinct vibration", signatures.add("${haptic.timingsMs}|${haptic.amplitudes}"))
        }
    }

    @Test
    fun isometricStartStopAndRestDoneFeelDifferent() {
        val go = CueCatalog.haptic(CueType.HOLD_GO)
        val stop = CueCatalog.haptic(CueType.HOLD_STOP)
        val rest = CueCatalog.haptic(CueType.REST_DONE)
        val ready = CueCatalog.haptic(CueType.GET_READY_START)
        assertEquals(1, go.pulseCount)
        assertEquals(2, stop.pulseCount)
        assertEquals(3, rest.pulseCount)
        assertEquals(2, ready.pulseCount)
        assertTrue(go.timingsMs[1] > 300)
    }
}
