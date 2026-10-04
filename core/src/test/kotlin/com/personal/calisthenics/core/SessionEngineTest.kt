package com.personal.calisthenics.core

import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.session.SessionEngine
import com.personal.calisthenics.core.session.SetResult
import com.personal.calisthenics.core.session.StageKind
import com.personal.calisthenics.core.session.newSessionEngine
import com.personal.calisthenics.core.timer.CueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private var now = 0L

    private fun engine(day: WorkoutDay = WorkoutDay.A, cold: Boolean = false, deload: Boolean = false): SessionEngine {
        now = 1_000L
        return newSessionEngine(PlanOptions(day, deload = deload, coldMode = cold))
    }

    /** Runs the clock forward in 100 ms steps until [done] is true; returns all cues that fired. */
    private fun SessionEngine.run(maxMs: Long = 4_000_000L, done: (SessionEngine) -> Boolean): List<CueType> {
        val cues = mutableListOf<CueType>()
        val end = now + maxMs
        while (now < end && !done(this)) {
            now += 100
            cues += tick(now)
        }
        assertTrue("condition not reached", done(this))
        return cues
    }

    private fun SessionEngine.stage() = snapshot(now).stage

    private fun SessionEngine.logDefault() {
        val snap = snapshot(now)
        logSet(SetResult(snap.suggestedReps, snap.suggestedHold, 2, 0), now)
    }

    @Test
    fun startsInWarmupFlowAndRollsThroughEveryDrill() {
        val e = engine()
        assertEquals(StageKind.NOT_STARTED, e.stage())
        e.begin(now)
        assertEquals(StageKind.FLOW_READY, e.stage())
        val flowItems = e.snapshot(now).flowItems
        assertTrue(flowItems.isNotEmpty())
        e.startFlow(now)
        assertEquals(StageKind.FLOW, e.stage())
        val cues = e.run { it.stage() != StageKind.FLOW }
        assertEquals(StageKind.READY, e.stage())
        assertTrue(CueType.FLOW_DONE in cues)
        assertEquals(flowItems.size, cues.count { it == CueType.FLOW_DRILL_START })
        assertEquals(flowItems.size, cues.count { it == CueType.FLOW_TRANSITION })
    }

    @Test
    fun isometricSetHasFiveSecondGetReadyThenHoldThenLogging() {
        val e = engine()
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        val item = e.snapshot(now).item!!
        assertEquals("l_sit", item.exerciseId)
        e.startSet(now)
        val snap = e.snapshot(now)
        assertEquals(StageKind.ACTIVE, snap.stage)
        assertEquals("Get ready", snap.timer!!.phase!!.label)
        val cues = e.run { it.stage() == StageKind.LOGGING }
        assertTrue(CueType.HOLD_GO in cues)
        assertTrue(CueType.HOLD_STOP in cues)
        assertEquals(item.holdMaxSec, e.snapshot(now).suggestedHold)
    }

    @Test
    fun loggingASetStartsTheSmartRestAndRestEndsIntoTheNextSet() {
        val e = engine()
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        e.startSet(now)
        e.run { it.stage() == StageKind.LOGGING }
        e.logDefault()
        val rest = e.snapshot(now)
        assertEquals(StageKind.REST, rest.stage)
        assertEquals(90, rest.restSeconds)
        assertEquals(1, rest.completedSets)
        val cues = e.run { it.stage() == StageKind.READY }
        assertTrue(CueType.REST_DONE in cues)
        assertEquals(2, e.snapshot(now).item!!.setNumber)
    }

    @Test
    fun restLengthFollowsRepsInReserve() {
        val e = engine()
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        // Skip the isometrics (6 sets), reach the pull-ups.
        while (e.snapshot(now).item!!.exerciseId != "strict_pullups") e.skipSet(now)
        val item = e.snapshot(now).item!!
        assertTrue(item.restMinSec < item.restMaxSec)
        e.startSet(now)
        e.stopSet(now + 5_000)
        now += 5_000
        e.logSet(SetResult(reps = 8, holdSeconds = null, rir = 0, levelIndex = 1), now)
        assertEquals(item.restMaxSec, e.snapshot(now).restSeconds)
        // Easier set -> shorter rest.
        e.run { it.stage() == StageKind.READY }
        e.startSet(now)
        e.stopSet(now + 5_000)
        now += 5_000
        e.logSet(SetResult(reps = 8, holdSeconds = null, rir = 3, levelIndex = 1), now)
        assertEquals(item.restMinSec, e.snapshot(now).restSeconds)
    }

    @Test
    fun stoppingATempoSetEarlyCountsOnlyCompletedReps() {
        val e = engine()
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        while (e.snapshot(now).item!!.exerciseId != "strict_pullups") e.skipSet(now)
        e.startSet(now)
        // 3 s lead-in, then tempo 3-1-X-0 = 5 s per rep. After 3 + 5*2 + 1 s we are inside rep 3's lowering.
        now += 14_000
        e.tick(now)
        e.stopSet(now)
        assertEquals(2, e.snapshot(now).suggestedReps)
    }

    @Test
    fun stoppingAnIsometricEarlyKeepsTheMeasuredHold() {
        val e = engine()
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        e.startSet(now)
        now += 5_000 + 12_000
        e.tick(now)
        e.stopSet(now)
        assertEquals(12, e.snapshot(now).suggestedHold)
    }

    @Test
    fun noRestAfterTheLastStrengthSetAndDecompressionFlowFollows() {
        val e = engine()
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        val total = e.totalSets
        var last: Int
        while (true) {
            val snap = e.snapshot(now)
            if (snap.stage != StageKind.READY) break
            last = snap.completedSets
            if (snap.nextItem == null) {
                // Last set of the strength block: after logging there is no rest, the cool-down flow is next.
                e.startSet(now)
                e.stopSet(now + 1_000); now += 1_000
                e.logSet(SetResult(5, null, 2, 0), now)
                assertEquals(StageKind.FLOW_READY, e.stage())
                assertEquals(com.personal.calisthenics.core.model.Phase.DECOMPRESSION, e.snapshot(now).phase)
                return
            }
            e.skipSet(now)
            assertTrue(last <= total)
        }
        throw AssertionError("never reached the last set")
    }

    @Test
    fun wholeSessionRunsToTheJointLogAndFinishes() {
        val e = engine(WorkoutDay.B)
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        var guard = 0
        while (e.stage() != StageKind.FLOW_READY && guard++ < 200) {
            when (e.stage()) {
                StageKind.READY -> { e.startSet(now); e.stopSet(now + 500); now += 500 }
                StageKind.LOGGING -> e.logDefault()
                StageKind.REST -> e.skipRest(now)
                else -> throw AssertionError("unexpected ${e.stage()}")
            }
        }
        assertEquals(e.totalSets, e.entries.size)
        e.startFlow(now)
        e.run { it.stage() == StageKind.JOINT_LOG }
        assertEquals(StageKind.JOINT_LOG, e.stage())
        e.finish(JointRating(2, 3, 1))
        assertEquals(StageKind.FINISHED, e.stage())
        assertEquals(3, e.rating!!.elbows)
    }

    @Test
    fun pauseFreezesTheActiveTimer() {
        val e = engine()
        e.begin(now); e.startFlow(now)
        now += 3_000; e.tick(now)
        e.pause(now)
        val before = e.snapshot(now).timer!!.totalElapsedMs
        now += 60_000; e.tick(now)
        assertTrue(e.snapshot(now).paused)
        assertEquals(before, e.snapshot(now).timer!!.totalElapsedMs)
        e.resume(now)
        now += 1_000
        assertTrue(e.snapshot(now).timer!!.totalElapsedMs > before)
    }

    @Test
    fun coldModeReordersSetsAndCapsRestAt90Seconds() {
        val e = engine(cold = true)
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        while (e.snapshot(now).item!!.exerciseId != "strict_pullups") e.skipSet(now)
        val pull = e.snapshot(now).item!!
        assertNotNull(pull.supersetWith)
        e.startSet(now); e.stopSet(now + 1000); now += 1000
        e.logSet(SetResult(6, null, 0, 0), now)
        val rest = e.snapshot(now)
        assertEquals(90, rest.restSeconds)
        // Superset partner (dips) comes right after the pull-up set.
        assertTrue(rest.nextItem!!.exerciseId.contains("dips"))
    }

    @Test
    fun coldModeCanBeToggledOnlyBeforeTheFirstLoggedSet() {
        val e = engine()
        e.begin(now)
        assertTrue(e.snapshot(now).coldModeChangeable)
        assertTrue(e.setColdMode(true))
        assertTrue(e.snapshot(now).coldMode)
        e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        e.startSet(now); e.stopSet(now + 500); now += 500
        e.logDefault()
        assertFalse(e.snapshot(now).coldModeChangeable)
        assertFalse(e.setColdMode(false))
        assertTrue(e.snapshot(now).coldMode)
    }

    @Test
    fun endEarlyKeepsLoggedSetsAndGoesToTheJointLog() {
        val e = engine()
        e.begin(now); e.startFlow(now)
        e.run { it.stage() == StageKind.READY }
        e.startSet(now); e.stopSet(now + 500); now += 500
        e.logDefault()
        e.endEarly()
        assertEquals(StageKind.JOINT_LOG, e.stage())
        assertEquals(1, e.entries.size)
        e.finish(null)
        assertNull(e.rating)
        assertEquals(StageKind.FINISHED, e.stage())
    }

    @Test
    fun deloadSessionHasFewerSets() {
        val normal = newSessionEngine(PlanOptions(WorkoutDay.A)).totalSets
        val deload = newSessionEngine(PlanOptions(WorkoutDay.A, deload = true)).totalSets
        assertTrue(deload < normal)
    }
}
