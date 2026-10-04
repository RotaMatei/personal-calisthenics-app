package com.personal.calisthenics.core

import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.plan.SessionPlanner
import com.personal.calisthenics.core.plan.WorkoutOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutOutlineTest {

    private fun outline(options: PlanOptions = PlanOptions(WorkoutDay.A)) = WorkoutOutline.of(SessionPlanner.plan(options))

    @Test
    fun everyStepOfThePlanIsListedOnceInPhaseOrder() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.B))
        val sections = WorkoutOutline.of(plan)
        assertEquals(Phase.entries.toList(), sections.map { it.phase })
        assertEquals(plan.steps.map { it.stepId }, sections.flatMap { it.rows }.map { it.stepId })
    }

    @Test
    fun strengthRowsGiveSetsRepsAndRestInMinutes() {
        val rows = outline().flatMap { it.rows }
        val pullups = rows.single { it.stepId == "p2_pullups" }
        assertEquals("4 x 6-10 reps", pullups.prescription)
        assertEquals("2.5-3 min", pullups.rest)
        assertEquals("3-1-X-0", pullups.tempo)
        val legs = rows.single { it.stepId == "p2_legs" }
        assertEquals("3 x 6-8 reps", legs.prescription)
        assertEquals("1 min", legs.rest)
        assertTrue("per-leg work is flagged", legs.notes.any { it.contains("side or leg") })
    }

    @Test
    fun isometricRowsGiveTheHoldInSeconds() {
        val lsit = outline().flatMap { it.rows }.single { it.stepId == "p1_lsit" }
        assertEquals("3 x 15-20 s hold", lsit.prescription)
        assertEquals("1.5 min", lsit.rest)
    }

    @Test
    fun drillsShowTheirDurationAndHaveNoRest() {
        val rows = outline().flatMap { it.rows }.filter { it.kind == StepKind.FLOW }
        assertTrue(rows.isNotEmpty())
        rows.forEach { assertNull("${it.stepId} has no rest", it.rest) }
        assertEquals("45 s", rows.single { it.stepId == "w0_jacks" }.prescription)
        assertEquals("2 x 30 s", rows.single { it.stepId == "p3_german" }.prescription)
        assertEquals("1 min 30 s", rows.single { it.stepId == "p3_squat" }.prescription)
        rows.forEach { assertTrue("${it.stepId} explains its drill", it.details.isNotEmpty() && it.details.all { d -> d.isNotBlank() }) }
        assertEquals(2, rows.single { it.stepId == "p3_german" }.details.size)
    }

    @Test
    fun deloadAndColdModeAreExplained() {
        val deload = outline(PlanOptions(WorkoutDay.A, deload = true)).flatMap { it.rows }.single { it.stepId == "p2_pullups" }
        assertTrue(deload.notes.any { it.startsWith("Deload week") })
        assertFalse(outline().flatMap { it.rows }.single { it.stepId == "p2_pullups" }.notes.any { it.startsWith("Deload") })

        val cold = outline(PlanOptions(WorkoutDay.A, coldMode = true)).flatMap { it.rows }
        val pull = cold.single { it.stepId == "p2_pullups" }
        assertEquals("1.5 min", pull.rest)
        assertNotNull(pull.notes.firstOrNull { it.startsWith("Superset with") })
        // Unpaired exercises keep their normal rest.
        assertEquals("1 min", cold.single { it.stepId == "p2_legs" }.rest)
    }

    @Test
    fun minuteFormatting() {
        assertEquals("2", WorkoutOutline.minutes(120))
        assertEquals("1.5", WorkoutOutline.minutes(90))
        assertEquals("1.25", WorkoutOutline.minutes(75))
        assertEquals("0.5", WorkoutOutline.minutes(30))
        assertEquals("2 min", WorkoutOutline.restRange(120, 120))
        assertEquals("2-3 min", WorkoutOutline.restRange(180, 120))
        assertEquals("1.5 min", WorkoutOutline.restRange(150, 180, capSec = 90))
    }
}
