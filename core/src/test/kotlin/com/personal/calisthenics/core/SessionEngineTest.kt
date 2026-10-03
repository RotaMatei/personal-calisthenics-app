package com.personal.calisthenics.core

import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.plan.SessionPlanner
import com.personal.calisthenics.core.session.SessionEngine
import com.personal.calisthenics.core.session.SessionMode
import com.personal.calisthenics.core.timer.CueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    /** Drives the engine with a fake clock; ticks every 100 ms like the service does. */
    private class Driver(val engine: SessionEngine) {
        var now = 1_000L
        val cues = mutableListOf<CueType>()
        fun advance(ms: Long) {
            var left = ms
            while (left > 0) {
                val step = minOf(100L, left)
                now += step
                left -= step
                cues += engine.poll(now)
            }
        }
        fun untilMode(mode: SessionMode, limitMs: Long = 3_600_000L) {
            var spent = 0L
            while (engine.mode != mode && spent < limitMs) { advance(100); spent += 100 }
            assertEquals(mode, engine.mode)
        }
    }

    private fun driver(options: PlanOptions = PlanOptions(WorkoutDay.A)) =
        Driver(SessionEngine(SessionPlanner.plan(options)))

    @Test
    fun warmupFlowRunsByItselfThenWaitsForTheFirstSet() {
        val d = driver()
        d.engine.start(d.now)
        assertEquals(SessionMode.FLOW, d.engine.mode)
        d.untilMode(SessionMode.READY)
        assertTrue(d.cues.contains(CueType.FLOW_TRANSITION))
        assertTrue(d.cues.contains(CueType.FLOW_DONE))
        assertEquals("Isometrics phase first", com.personal.calisthenics.core.model.Phase.ISOMETRICS, d.engine.snapshot(d.now).phase)
    }

    @Test
    fun isometricSetHasGetReadyHoldThenLogThenSmartRest() {
        val d = driver()
        d.engine.start(d.now)
        d.untilMode(SessionMode.READY)
        val item = d.engine.snapshot(d.now).item!!
        assertEquals(StepKind.ISOMETRIC, item.kind)
        d.engine.startSet(d.now)
        assertEquals(SessionMode.ACTIVE, d.engine.mode)
        d.cues.clear()
        d.untilMode(SessionMode.AWAITING_LOG)
        assertTrue(d.cues.contains(CueType.HOLD_GO))
        assertTrue(d.cues.contains(CueType.HOLD_STOP))
        assertEquals(item.holdMaxSec, d.engine.snapshot(d.now).suggestion.holdSeconds)
        val result = d.engine.logSet(d.now, null, item.holdMaxSec, null)
        assertNotNull(result)
        assertEquals(SessionMode.RESTING, d.engine.mode)
        assertEquals(90, d.engine.snapshot(d.now).restSeconds)
        d.untilMode(SessionMode.READY)
        // Next set of the same step.
        assertEquals(2, d.engine.snapshot(d.now).item!!.setNumber)
        assertEquals(1, d.engine.snapshot(d.now).setsLogged)
    }

    @Test
    fun stoppingAnIsometricEarlySuggestsTheHeldSeconds() {
        val d = driver()
        d.engine.start(d.now)
        d.untilMode(SessionMode.READY)
        d.engine.startSet(d.now)
        d.advance(5_000 + 7_400)
        d.engine.finishSetEarly(d.now)
        assertEquals(SessionMode.AWAITING_LOG, d.engine.mode)
        assertEquals(7, d.engine.snapshot(d.now).suggestion.holdSeconds)
    }

    @Test
    fun tempoSetStopsEarlyWithCompletedRepCount() {
        val d = driver()
        d.engine.start(d.now)
        // Skip to the first strength set by logging every isometric set instantly.
        d.untilMode(SessionMode.READY)
        while (d.engine.snapshot(d.now).item!!.kind == StepKind.ISOMETRIC) {
            d.engine.startSet(d.now); d.engine.finishSetEarly(d.now)
            d.engine.logSet(d.now, null, 15, null)
            d.engine.skipRest(d.now)
        }
        val item = d.engine.snapshot(d.now).item!!
        assertEquals(StepKind.STRENGTH, item.kind)
        val tempo = item.tempo!!
        d.engine.startSet(d.now)
        // 3 s lead-in + two full reps + a bit into the third.
        d.advance(3_000L + 2L * tempo.repSeconds * 1000L + 500L)
        d.engine.finishSetEarly(d.now)
        assertEquals(2, d.engine.snapshot(d.now).suggestion.reps)
    }

    @Test
    fun restUsesRirAndColdModeCapsPairedExercises() {
        val d = driver(PlanOptions(WorkoutDay.A, coldMode = true))
        d.engine.start(d.now)
        d.untilMode(SessionMode.READY)
        while (d.engine.snapshot(d.now).item!!.supersetWith == null) {
            d.engine.startSet(d.now); d.engine.finishSetEarly(d.now)
            d.engine.logSet(d.now, 5, 15, 2)
            d.engine.skipRest(d.now)
        }
        d.engine.startSet(d.now); d.engine.finishSetEarly(d.now)
        d.engine.logSet(d.now, 6, null, 0)
        assertEquals(90, d.engine.snapshot(d.now).restSeconds)
    }

    @Test
    fun fullSessionWalksEveryBlockAndFinishesWithoutRestAfterLastSet() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.B))
        val engine = SessionEngine(plan)
        val d = Driver(engine)
        engine.start(d.now)
        var guard = 0
        while (engine.mode != SessionMode.FINISHED && guard++ < 10_000) {
            when (engine.mode) {
                SessionMode.FLOW -> d.advance(1_000)
                SessionMode.READY -> engine.startSet(d.now)
                SessionMode.ACTIVE -> engine.finishSetEarly(d.now)
                SessionMode.AWAITING_LOG -> engine.logSet(d.now, 8, 15, 2)
                SessionMode.RESTING -> engine.skipRest(d.now)
                else -> break
            }
        }
        assertEquals(SessionMode.FINISHED, engine.mode)
        assertEquals(plan.workItems.size, engine.loggedResults.size)
        assertEquals(plan.workItems.map { it.key }, engine.loggedResults.map { it.item.key })
    }

    @Test
    fun pauseFreezesTheTimer() {
        val d = driver()
        d.engine.start(d.now)
        d.advance(3_000)
        val before = d.engine.snapshot(d.now).timer!!.totalElapsedMs
        d.engine.pause(d.now)
        d.advance(20_000)
        assertEquals(before, d.engine.snapshot(d.now).timer!!.totalElapsedMs)
        d.engine.resume(d.now)
        d.advance(1_000)
        assertEquals(before + 1_000, d.engine.snapshot(d.now).timer!!.totalElapsedMs)
    }

    @Test
    fun extendRestAddsThirtySeconds() {
        val d = driver()
        d.engine.start(d.now)
        d.untilMode(SessionMode.READY)
        d.engine.startSet(d.now); d.engine.finishSetEarly(d.now); d.engine.logSet(d.now, null, 15, null)
        d.advance(10_000)
        d.engine.extendRest(d.now, 30)
        assertEquals(110, d.engine.snapshot(d.now).restSeconds)
    }
}

class ChecklistTest {
    @Test
    fun checklistHasNutritionAndGearItemsWithUniqueIds() {
        val items = com.personal.calisthenics.core.plan.PreWorkoutChecklist.items
        assertEquals(items.size, items.map { it.id }.toSet().size)
        assertEquals(3, com.personal.calisthenics.core.plan.PreWorkoutChecklist.byGroup(com.personal.calisthenics.core.plan.ChecklistGroup.NUTRITION).size)
        assertEquals(3, com.personal.calisthenics.core.plan.PreWorkoutChecklist.byGroup(com.personal.calisthenics.core.plan.ChecklistGroup.GEAR).size)
    }
}

class LineScaleTest {
    @Test
    fun scaleStartsAtZeroWithRoundTicks() {
        val s = com.personal.calisthenics.core.analytics.LineScale.of(listOf(12.0, 18.0, 20.0))
        assertEquals(0.0, s.ticks.first(), 0.0001)
        assertTrue(s.max >= 20.0)
        assertEquals(1f, s.fraction(s.max), 0.0001f)
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0), s.ticks)
    }

    @Test
    fun emptyAndZeroDataStillGiveAnAxis() {
        assertEquals(2, com.personal.calisthenics.core.analytics.LineScale.of(emptyList()).ticks.size)
        assertEquals(2, com.personal.calisthenics.core.analytics.LineScale.of(listOf(0.0)).ticks.size)
    }
}
