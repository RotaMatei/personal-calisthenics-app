package com.personal.calisthenics.core

import com.personal.calisthenics.core.model.HighlightKind
import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.model.Tempo
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    @Test
    fun hasTwentySevenUniqueExercises() {
        assertEquals(27, SeedData.exercises.size)
        assertEquals(27, SeedData.exercises.map { it.id }.toSet().size)
    }

    @Test
    fun everyStepReferencesKnownExercises() {
        for (step in SeedData.steps) {
            assertNotNull(step.id, SeedData.exerciseOrNull(step.exerciseId))
            for (id in step.exerciseByDay.values) assertNotNull(id, SeedData.exerciseOrNull(id))
        }
    }

    @Test
    fun everyExerciseHasGuideContent() {
        for (e in SeedData.exercises) {
            assertTrue("${e.id} highlights", e.highlights.isNotEmpty())
            assertTrue("${e.id} has a red muscle", e.highlights.any { it.kind == HighlightKind.MUSCLE })
            assertTrue("${e.id} setup cues", e.setupCues.isNotEmpty())
            assertTrue("${e.id} execution cues", e.executionCues.isNotEmpty())
            assertTrue("${e.id} do/dont", e.doDonts.isNotEmpty())
            assertTrue("${e.id} ladder", e.ladder.size >= 3)
            assertTrue("${e.id} start level", e.startLevel in e.ladder.indices)
            assertTrue("${e.id} protection note", e.jointProtection.isNotBlank())
        }
    }

    @Test
    fun everyExerciseIsUsedBySomeStep() {
        val used = SeedData.steps.flatMap { listOf(it.exerciseId) + it.exerciseByDay.values }.toSet()
        for (e in SeedData.exercises) assertTrue("${e.id} unused", e.id in used)
    }

    @Test
    fun phaseOneAndTwoNumberingMatchesSpec() {
        val numbered = SeedData.steps.filter { it.number != null }.sortedBy { it.number }
        assertEquals((1..9).toList(), numbered.map { it.number })
        assertEquals(Phase.ISOMETRICS, numbered[0].phase)
        assertEquals(Phase.ISOMETRICS, numbered[1].phase)
        for (i in 2..8) assertEquals(Phase.STRENGTH, numbered[i].phase)
    }

    @Test
    fun isometricPrescriptions() {
        for (id in listOf("p1_lsit", "p1_planche")) {
            val s = SeedData.step(id)
            assertEquals(StepKind.ISOMETRIC, s.kind)
            assertEquals(3, s.sets)
            assertEquals(15, s.holdMinSec)
            assertEquals(20, s.holdMaxSec)
            assertEquals(90, s.restMinSec)
            assertEquals(90, s.restMaxSec)
        }
    }

    @Test
    fun strengthPrescriptionsMatchSpec() {
        data class Spec(val id: String, val sets: Int, val repsMin: Int, val repsMax: Int, val tempo: String, val restMin: Int, val restMax: Int)
        val specs = listOf(
            Spec("p2_pullups", 4, 6, 10, "3-1-X-0", 150, 180),
            Spec("p2_dips", 4, 8, 12, "3-0-1-0", 120, 150),
            Spec("p2_aus", 3, 8, 12, "2-0-1-1", 120, 120),
            Spec("p2_pike", 3, 6, 10, "3-1-1-0", 120, 120),
            Spec("p2_legs", 3, 6, 8, "3-1-1-0", 60, 60),
            Spec("p2_nordic", 3, 8, 10, "4-0-1-0", 90, 90),
            Spec("p2_raises", 3, 8, 12, "3-0-1-0", 90, 90),
        )
        for (spec in specs) {
            val s = SeedData.step(spec.id)
            assertEquals(spec.id, spec.sets, s.sets)
            assertEquals(spec.id, spec.repsMin, s.repsMin)
            assertEquals(spec.id, spec.repsMax, s.repsMax)
            assertEquals(spec.id, spec.tempo, s.tempo!!.notation)
            assertEquals(spec.id, spec.restMin, s.restMinSec)
            assertEquals(spec.id, spec.restMax, s.restMaxSec)
        }
        assertTrue(SeedData.step("p2_legs").perSide)
    }

    @Test
    fun tempoParsing() {
        val t = Tempo.parse("3-1-X-0")
        assertTrue(t.explosive)
        assertEquals(3, t.eccentricSec)
        assertEquals(1, t.bottomPauseSec)
        assertEquals(1, t.concentricSec)
        assertEquals(0, t.topPauseSec)
        assertEquals("3-1-X-0", t.notation)
        assertEquals(5, t.repSeconds)
        assertEquals("2-0-1-1", Tempo.parse("2-0-1-1").notation)
    }

    @Test
    fun dayVariantsAreDefined() {
        val dips = SeedData.step("p2_dips")
        assertEquals("straight_bar_dips", dips.exerciseFor(WorkoutDay.C))
        assertEquals("parallel_bar_dips", dips.exerciseFor(WorkoutDay.A))
        assertEquals("parallel_bar_dips", dips.exerciseFor(WorkoutDay.B))
        val push = SeedData.step("p2_pike")
        assertEquals("pseudo_planche_pushups", push.exerciseFor(WorkoutDay.B))
        assertEquals("pike_pushups", push.exerciseFor(WorkoutDay.A))
        assertEquals("pike_pushups", push.exerciseFor(WorkoutDay.C))
    }

    @Test
    fun flowPhasesHaveDrills() {
        val warmup = SeedData.stepsIn(Phase.WARMUP)
        assertEquals(12, warmup.size)
        assertTrue(warmup.all { it.kind == StepKind.FLOW && it.flowParts.isNotEmpty() })
        val decompression = SeedData.stepsIn(Phase.DECOMPRESSION)
        assertEquals(5, decompression.size)
        val parts = decompression.flatMap { it.flowParts }
        assertEquals(2 + 2 + 4 + 1 + 2, parts.size)
        assertEquals(30, SeedData.step("p3_german").flowParts[0].seconds)
        assertEquals(45, SeedData.step("p3_hang").flowParts[0].seconds)
        assertEquals(90, SeedData.step("p3_squat").flowParts[0].seconds)
    }

    @Test
    fun partnersAreSymmetric() {
        assertEquals("p2_dips", SeedData.step("p2_pullups").partnerStepId)
        assertEquals("p2_pullups", SeedData.step("p2_dips").partnerStepId)
        assertEquals("p2_pike", SeedData.step("p2_aus").partnerStepId)
        assertEquals("p2_aus", SeedData.step("p2_pike").partnerStepId)
        assertFalse(SeedData.steps.any { it.partnerStepId != null && it.number !in 3..6 })
    }
}
