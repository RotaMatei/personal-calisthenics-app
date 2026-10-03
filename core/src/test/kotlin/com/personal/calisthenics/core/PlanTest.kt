package com.personal.calisthenics.core

import com.personal.calisthenics.core.model.Grip
import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenics.core.plan.DayRotation
import com.personal.calisthenics.core.plan.FlowBlock
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.plan.RestPlanner
import com.personal.calisthenics.core.plan.SessionPlanner
import com.personal.calisthenics.core.plan.SetBlock
import com.personal.calisthenics.core.plan.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanTest {

    @Test
    fun rotationCyclesAbcAndStartsAtA() {
        assertEquals(WorkoutDay.A, DayRotation.next(null))
        assertEquals(WorkoutDay.B, DayRotation.next(WorkoutDay.A))
        assertEquals(WorkoutDay.C, DayRotation.next(WorkoutDay.B))
        assertEquals(WorkoutDay.A, DayRotation.next(WorkoutDay.C))
    }

    @Test
    fun deloadWeekIsWeekSixOfEveryBlock() {
        val start = LocalDate.of(2026, 9, 28) // a Monday
        assertEquals(1, Deload.weekInBlock(start, LocalDate.of(2026, 9, 28)))
        assertEquals(1, Deload.weekInBlock(start, LocalDate.of(2026, 10, 4)))
        assertEquals(2, Deload.weekInBlock(start, LocalDate.of(2026, 10, 5)))
        assertEquals(5, Deload.weekInBlock(start, LocalDate.of(2026, 10, 26)))
        assertEquals(6, Deload.weekInBlock(start, LocalDate.of(2026, 11, 2)))
        assertTrue(Deload.isDeloadWeek(start, LocalDate.of(2026, 11, 8)))
        assertEquals(1, Deload.weekInBlock(start, LocalDate.of(2026, 11, 9)))
        assertEquals(2, Deload.blockNumber(start, LocalDate.of(2026, 11, 9)))
        assertEquals(1, Deload.blockNumber(start, LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun blockStartIsNormalizedToMonday() {
        val wednesday = LocalDate.of(2026, 9, 30)
        assertEquals(LocalDate.of(2026, 9, 28), Deload.normalizeStart(wednesday))
        assertEquals(1, Deload.weekInBlock(wednesday, LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun weeksUntilDeloadCountsDown() {
        val start = LocalDate.of(2026, 9, 28)
        assertEquals(5, Deload.weeksUntilDeload(start, LocalDate.of(2026, 9, 28)))
        assertEquals(1, Deload.weeksUntilDeload(start, LocalDate.of(2026, 10, 26)))
        assertEquals(0, Deload.weeksUntilDeload(start, LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun deloadHalvesTotalSetsWithAtLeastOnePerExercise() {
        val planned = listOf(3, 3, 4, 4, 3, 3, 3, 3, 3)
        val reduced = Deload.deloadSets(planned)
        assertEquals(planned.size, reduced.size)
        assertEquals(15, reduced.sum())
        assertTrue(reduced.all { it >= 1 })
        for (i in planned.indices) assertTrue(reduced[i] <= planned[i])
        assertEquals(listOf(2, 2, 2, 2, 2, 2, 1, 1, 1), reduced)
    }

    @Test
    fun deloadEdgeCases() {
        assertEquals(emptyList<Int>(), Deload.deloadSets(emptyList()))
        assertEquals(listOf(1), Deload.deloadSets(listOf(1)))
        assertEquals(listOf(2, 2), Deload.deloadSets(listOf(4, 4)))
        assertEquals(listOf(1, 1, 1), Deload.deloadSets(listOf(1, 1, 1)))
    }

    @Test
    fun dayAUsesOverhandPikeAndParallelDips() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.A))
        val titles = plan.steps.associate { it.stepId to it.title }
        assertEquals("Strict Pull-ups (Overhand)", titles["p2_pullups"])
        assertEquals("Pike Push-ups", titles["p2_pike"])
        assertEquals("Parallel Bar Dips", titles["p2_dips"])
    }

    @Test
    fun dayBUsesNeutralGripAndPseudoPlanche() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.B))
        val byId = plan.steps.associateBy { it.stepId }
        assertEquals("Strict Pull-ups (Neutral Grip)", byId["p2_pullups"]!!.title)
        assertEquals("pseudo_planche_pushups", byId["p2_pike"]!!.exerciseId)
        assertEquals("Pseudo Planche Push-ups", byId["p2_pike"]!!.title)
        assertEquals("parallel_bar_dips", byId["p2_dips"]!!.exerciseId)
    }

    @Test
    fun dayCUsesChinUpsAndStraightBarDips() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.C))
        val byId = plan.steps.associateBy { it.stepId }
        assertEquals("Chin-ups (Supinated)", byId["p2_pullups"]!!.title)
        assertEquals("straight_bar_dips", byId["p2_dips"]!!.exerciseId)
        assertEquals("Straight Bar Dips", byId["p2_dips"]!!.title)
        assertEquals("pike_pushups", byId["p2_pike"]!!.exerciseId)
    }

    @Test
    fun gripOverrideReplacesThePullGrip() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.A, pullGrip = Grip.NEUTRAL))
        val pull = plan.steps.first { it.stepId == "p2_pullups" }
        assertEquals("Strict Pull-ups (Neutral Grip)", pull.title)
    }

    @Test
    fun normalSessionRunsAllSetsOfAStepBackToBack() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.A))
        val strength = plan.blocks.filterIsInstance<SetBlock>().first { it.phase == Phase.STRENGTH }
        val ids = strength.items.map { it.stepId }
        assertEquals(List(4) { "p2_pullups" } + List(4) { "p2_dips" }, ids.take(8))
        assertEquals(4 + 4 + 3 + 3 + 6 + 3 + 3, strength.items.size)
        assertTrue(strength.items.all { it.supersetWith == null })
    }

    @Test
    fun coldModePairsThreeWithFourAndFiveWithSix() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.A, coldMode = true))
        val strength = plan.blocks.filterIsInstance<SetBlock>().first { it.phase == Phase.STRENGTH }
        val ids = strength.items.map { it.stepId }
        val expectedFirst = (1..4).flatMap { listOf("p2_pullups", "p2_dips") }
        assertEquals(expectedFirst, ids.take(8))
        val expectedSecond = (1..3).flatMap { listOf("p2_aus", "p2_pike") }
        assertEquals(expectedSecond, ids.subList(8, 14))
        assertTrue(strength.items.take(14).all { it.supersetWith != null })
        assertTrue(strength.items.drop(14).all { it.supersetWith == null })
        assertEquals(strength.items.size, 4 + 4 + 3 + 3 + 6 + 3 + 3)
    }

    @Test
    fun coldModeHandlesUnequalSetCountsAfterDeload() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.A, deload = true, coldMode = true))
        val strength = plan.blocks.filterIsInstance<SetBlock>().first { it.phase == Phase.STRENGTH }
        val perStep = strength.items.groupingBy { it.stepId }.eachCount()
        val planned = plan.steps.associateBy { it.stepId }
        for ((id, count) in perStep) {
            val expected = planned[id]!!.sets * (if (planned[id]!!.perSide) 2 else 1)
            assertEquals(id, expected, count)
        }
    }

    @Test
    fun deloadSessionHalvesPhaseOneAndTwoButKeepsWarmupAndDecompression() {
        val normal = SessionPlanner.plan(PlanOptions(WorkoutDay.B))
        val deload = SessionPlanner.plan(PlanOptions(WorkoutDay.B, deload = true))
        fun loadedSets(plan: com.personal.calisthenics.core.plan.SessionPlan) =
            plan.steps.filter { it.phase == Phase.ISOMETRICS || it.phase == Phase.STRENGTH }.sumOf { it.sets }
        assertEquals(29, loadedSets(normal))
        assertEquals(15, loadedSets(deload))
        val flowSeconds = { p: com.personal.calisthenics.core.plan.SessionPlan ->
            p.blocks.filterIsInstance<FlowBlock>().sumOf { it.totalSeconds }
        }
        assertEquals(flowSeconds(normal), flowSeconds(deload))
        assertTrue(deload.steps.filter { it.kind != StepKind.FLOW }.all { it.sets <= it.originalSets && it.sets >= 1 })
    }

    @Test
    fun pistolSquatsAreSplitIntoLeftAndRight() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.A))
        val legs = plan.workItems.filter { it.stepId == "p2_legs" }
        assertEquals(6, legs.size)
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT), legs.map { it.side })
        assertEquals(60, legs.first().restMaxSec)
        assertNull(plan.workItems.first { it.stepId == "p2_pullups" }.side)
    }

    @Test
    fun workItemKeysAreUnique() {
        for (day in WorkoutDay.entries) {
            for (cold in listOf(false, true)) {
                val keys = SessionPlanner.plan(PlanOptions(day, coldMode = cold)).workItems.map { it.key }
                assertEquals(keys.size, keys.toSet().size)
            }
        }
    }

    @Test
    fun blockOrderFollowsTheFourPhases() {
        val plan = SessionPlanner.plan(PlanOptions(WorkoutDay.A))
        assertEquals(listOf(Phase.WARMUP, Phase.ISOMETRICS, Phase.STRENGTH, Phase.DECOMPRESSION), plan.blocks.map { it.phase })
        assertTrue(plan.blocks[0] is FlowBlock)
        assertTrue(plan.blocks[3] is FlowBlock)
    }

    @Test
    fun smartRestUsesRirAndColdCap() {
        assertEquals(180, RestPlanner.restSeconds(150, 180, 0))
        assertEquals(180, RestPlanner.restSeconds(150, 180, null))
        assertEquals(165, RestPlanner.restSeconds(150, 180, 1))
        assertEquals(150, RestPlanner.restSeconds(150, 180, 2))
        assertEquals(120, RestPlanner.restSeconds(120, 120, 0))
        assertEquals(90, RestPlanner.restSeconds(150, 180, 0, RestPlanner.COLD_MODE_REST_SEC))
        assertEquals(60, RestPlanner.restSeconds(60, 60, 3, RestPlanner.COLD_MODE_REST_SEC))
    }

    @Test
    fun estimatedDurationIsSensibleAndColdModeIsShorter() {
        val normal = SessionPlanner.plan(PlanOptions(WorkoutDay.A)).estimatedMinutes()
        val cold = SessionPlanner.plan(PlanOptions(WorkoutDay.A, coldMode = true)).estimatedMinutes()
        val deload = SessionPlanner.plan(PlanOptions(WorkoutDay.A, deload = true)).estimatedMinutes()
        assertTrue("normal $normal", normal.first in 60..130 && normal.last in 60..140)
        assertTrue(cold.last < normal.last)
        assertTrue(deload.last < normal.last)
        assertTrue(normal.first <= normal.last)
    }
}
