package com.personal.calisthenics.core

import com.personal.calisthenics.core.analytics.CellKind
import com.personal.calisthenics.core.analytics.ChartSeries
import com.personal.calisthenics.core.analytics.Heatmap
import com.personal.calisthenics.core.analytics.JointAdvice
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.analytics.LoggedSet
import com.personal.calisthenics.core.analytics.ProgressionGate
import com.personal.calisthenics.core.analytics.ProgressionState
import com.personal.calisthenics.core.analytics.Recovery
import com.personal.calisthenics.core.analytics.RecoveryState
import com.personal.calisthenics.core.analytics.Streaks
import com.personal.calisthenics.core.analytics.VolumeGuard
import com.personal.calisthenics.core.model.Grip
import com.personal.calisthenics.core.model.MovementCategory
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class AnalyticsTest {
    private val zone = ZoneId.of("UTC")
    private val hour = 3_600_000L

    private fun set(
        date: LocalDate,
        exercise: String,
        category: MovementCategory,
        reps: Int? = null,
        hold: Int? = null,
        rir: Int? = null,
        session: Long = date.toEpochDay(),
    ) = LoggedSet(
        sessionId = session,
        epochMs = date.atStartOfDay(zone).toInstant().toEpochMilli() + 10 * hour,
        exerciseId = exercise,
        category = category,
        reps = reps,
        holdSeconds = hold,
        rir = rir,
        levelIndex = 1,
    )

    // ------------------------------------------------------------------ recovery

    @Test
    fun recoveryWindowStates() {
        val last = 1_000_000L
        assertEquals(RecoveryState.NO_HISTORY, Recovery.status(null, last).state)
        assertEquals(RecoveryState.TOO_SOON, Recovery.status(last, last + 10 * hour).state)
        assertEquals(RecoveryState.TOO_SOON, Recovery.status(last, last + 47 * hour + 59 * 60_000L).state)
        assertEquals(RecoveryState.OPTIMAL_WINDOW, Recovery.status(last, last + 48 * hour).state)
        assertEquals(RecoveryState.OPTIMAL_WINDOW, Recovery.status(last, last + 72 * hour).state)
        assertEquals(RecoveryState.OVERDUE, Recovery.status(last, last + 73 * hour).state)
    }

    @Test
    fun recoveryProgressAndFormatting() {
        val last = 0L
        assertEquals(0.5f, Recovery.status(last, 36 * hour).progress, 0.001f)
        assertEquals(1f, Recovery.status(last, 100 * hour).progress, 0.001f)
        assertEquals("1d 4h 12m", Recovery.formatElapsed(28 * hour + 12 * 60_000L))
        assertEquals("3h 5m", Recovery.formatElapsed(3 * hour + 5 * 60_000L))
        assertEquals("7m", Recovery.formatElapsed(7 * 60_000L))
        val soon = Recovery.status(last, 40 * hour)
        assertEquals(8.0, soon.hoursUntilWindow, 0.01)
    }

    // ------------------------------------------------------------------ volume guard

    @Test
    fun spikeIsStrictlyMoreThanFifteenPercent() {
        assertTrue(VolumeGuard.isSpike(116, 100))
        assertFalse(VolumeGuard.isSpike(115, 100))
        assertFalse(VolumeGuard.isSpike(100, 100))
        assertFalse(VolumeGuard.isSpike(50, 0))
        assertTrue(VolumeGuard.isSpike(24, 20))
        assertFalse(VolumeGuard.isSpike(23, 20))
        assertEquals(20.0, VolumeGuard.changePercent(120, 100)!!, 0.001)
        assertNull(VolumeGuard.changePercent(10, 0))
    }

    @Test
    fun weeklyVolumesGroupByMondayWeek() {
        val monday = LocalDate.of(2026, 9, 28)
        val sets = listOf(
            set(monday, "strict_pullups", MovementCategory.PULL, reps = 8),
            set(monday.plusDays(2), "australian_pullups", MovementCategory.PULL, reps = 10),
            set(monday.plusDays(2), "parallel_bar_dips", MovementCategory.PUSH, reps = 9),
            set(monday.plusDays(2), "pistol_squat", MovementCategory.LEGS, reps = 7),
            set(monday.plusDays(7), "strict_pullups", MovementCategory.PULL, reps = 6),
            set(monday.plusDays(1), "l_sit", MovementCategory.ISOMETRIC, hold = 20),
        )
        val weeks = VolumeGuard.weeklyVolumes(sets, zone)
        assertEquals(2, weeks.size)
        assertEquals(18, weeks[monday]!!.pullReps)
        assertEquals(9, weeks[monday]!!.pushReps)
        assertEquals(6, weeks[monday.plusDays(7)]!!.pullReps)
    }

    @Test
    fun guardWarnsOnPullSpikeWithTheSpecMessage() {
        val w1 = LocalDate.of(2026, 9, 28)
        val w2 = w1.plusWeeks(1)
        val sets = listOf(
            set(w1, "strict_pullups", MovementCategory.PULL, reps = 100),
            set(w2, "strict_pullups", MovementCategory.PULL, reps = 116),
        )
        val weeks = VolumeGuard.weeklyVolumes(sets, zone)
        val check = VolumeGuard.evaluate(weeks, w2) { false }
        assertTrue(check.pullSpike)
        assertEquals(16.0, check.pullChangePercent!!, 0.001)
        assertTrue(check.warning!!.startsWith("Medial Epicondylitis (Golfer's Elbow) Risk"))
        assertTrue(check.warning!!.contains("spiked by >15%"))

        val ok = VolumeGuard.evaluate(
            VolumeGuard.weeklyVolumes(listOf(
                set(w1, "strict_pullups", MovementCategory.PULL, reps = 100),
                set(w2, "strict_pullups", MovementCategory.PULL, reps = 115),
            ), zone),
            w2,
        ) { false }
        assertFalse(ok.pullSpike)
        assertNull(ok.warning)
    }

    @Test
    fun deloadWeekIsSkippedAsBaseline() {
        val w5 = LocalDate.of(2026, 10, 26)
        val w6 = w5.plusWeeks(1)
        val w1 = w6.plusWeeks(1)
        val sets = listOf(
            set(w5, "strict_pullups", MovementCategory.PULL, reps = 100),
            set(w6, "strict_pullups", MovementCategory.PULL, reps = 50),
            set(w1, "strict_pullups", MovementCategory.PULL, reps = 105),
        )
        val weeks = VolumeGuard.weeklyVolumes(sets, zone)
        val withSkip = VolumeGuard.evaluate(weeks, w1) { it == w6 }
        assertFalse(withSkip.pullSpike)
        assertEquals(w5, withSkip.baseline!!.weekStart)
        val naive = VolumeGuard.evaluate(weeks, w1) { false }
        assertTrue(naive.pullSpike)
    }

    @Test
    fun noBaselineMeansNoWarning() {
        val w = LocalDate.of(2026, 9, 28)
        val weeks = VolumeGuard.weeklyVolumes(listOf(set(w, "strict_pullups", MovementCategory.PULL, reps = 40)), zone)
        val check = VolumeGuard.evaluate(weeks, w) { false }
        assertNull(check.baseline)
        assertFalse(check.pullSpike)
    }

    // ------------------------------------------------------------------ streak & heatmap

    @Test
    fun weeklyStreakCountsWeeksWithThreeSessions() {
        val today = LocalDate.of(2026, 10, 3) // Saturday
        val monday = LocalDate.of(2026, 9, 28)
        val dates = mutableListOf<LocalDate>()
        for (week in 0..2) {
            val start = monday.minusWeeks(week.toLong())
            dates += listOf(start, start.plusDays(2), start.plusDays(4))
        }
        assertEquals(3, Streaks.weeklyStreak(dates, today))
        // The current week is still in progress with only two sessions, so the streak survives.
        val inProgress = dates.filterNot { it == monday.plusDays(4) }
        assertEquals(2, Streaks.weeklyStreak(inProgress, today))
        assertEquals(0, Streaks.weeklyStreak(emptyList(), today))
        // A missed week breaks the streak.
        val gap = dates.filter { it.isBefore(monday.minusWeeks(1)) || it.isAfter(monday.minusDays(1)) }
        assertEquals(1, Streaks.weeklyStreak(gap, today))
    }

    @Test
    fun heatmapMarksWorkoutsRestDaysAndDeloadWeeks() {
        val today = LocalDate.of(2026, 10, 3)
        val sessions = mapOf(
            LocalDate.of(2026, 10, 1) to WorkoutDay.A,
            LocalDate.of(2026, 10, 2) to WorkoutDay.B,
            LocalDate.of(2026, 9, 30) to WorkoutDay.C,
        )
        val blockStart = LocalDate.of(2026, 9, 28)
        val month = Heatmap.month(YearMonth.of(2026, 10), sessions, blockStart, today)
        assertEquals(5, month.weeks.size)
        assertTrue(month.weeks.all { it.size == 7 })
        // 1 October 2026 is a Thursday, so Monday-Wednesday of the first week are padding.
        assertNull(month.weeks[0][0])
        assertNull(month.weeks[0][2])
        val thursday = month.weeks[0][3]!!
        assertEquals(LocalDate.of(2026, 10, 1), thursday.date)
        assertEquals(CellKind.WORKOUT_A, thursday.kind)
        assertEquals(CellKind.WORKOUT_B, month.weeks[0][4]!!.kind)
        assertEquals(CellKind.REST, month.weeks[0][5]!!.kind)
        assertTrue(month.weeks[0][5]!!.isToday)
        assertEquals(CellKind.FUTURE, month.weeks[0][6]!!.kind)
        assertEquals(2, month.workoutCount)
        // Deload is week 6 of the block: 2026-11-02 week. The October grid has none.
        assertTrue(month.weeks.flatten().filterNotNull().none { it.deloadWeek })
        val november = Heatmap.month(YearMonth.of(2026, 11), emptyMap(), blockStart, today)
        val deloadDays = november.weeks.flatten().filterNotNull().filter { it.deloadWeek }
        assertEquals(7, deloadDays.size)
        assertEquals(LocalDate.of(2026, 11, 2), deloadDays.first().date)
    }

    // ------------------------------------------------------------------ joint advice

    @Test
    fun elbowStiffnessAboveTwoSwitchesToNeutralGrip() {
        val advice = JointAdvice.evaluate(JointRating(wrists = 1, elbows = 3, shoulders = 1))
        assertTrue(advice.forceNeutralGrip)
        assertEquals(Grip.NEUTRAL, advice.gripFor(Grip.CHIN))
        assertTrue(advice.messages.first().contains("Neutral Grip"))
        val fine = JointAdvice.evaluate(JointRating(wrists = 2, elbows = 2, shoulders = 2))
        assertFalse(fine.forceNeutralGrip)
        assertEquals(Grip.OVERHAND, fine.gripFor(Grip.OVERHAND))
        assertTrue(fine.messages.isEmpty())
        assertFalse(JointAdvice.evaluate(null).forceNeutralGrip)
    }

    @Test
    fun wristAndShoulderStiffnessProduceAdviceButKeepTheGrip() {
        val advice = JointAdvice.evaluate(JointRating(wrists = 4, elbows = 1, shoulders = 5))
        assertFalse(advice.forceNeutralGrip)
        assertEquals(2, advice.messages.size)
    }

    // ------------------------------------------------------------------ progression

    @Test
    fun progressionGateNeedsStagesOneToThreeBeforeUnlock() {
        val ex = SeedData.exercise("pike_pushups")
        var mask = 0
        assertFalse(ProgressionGate.readyToUnlock(mask))
        mask = ProgressionGate.toggle(mask, 1)
        mask = ProgressionGate.toggle(mask, 2)
        assertTrue(ProgressionGate.isDone(mask, 1))
        assertFalse(ProgressionGate.isDone(mask, 3))
        assertFalse(ProgressionGate.readyToUnlock(mask))
        mask = ProgressionGate.toggle(mask, 3)
        assertTrue(ProgressionGate.readyToUnlock(mask))
        mask = ProgressionGate.toggle(mask, 2) // untick
        assertFalse(ProgressionGate.readyToUnlock(mask))
        mask = ProgressionGate.toggle(mask, 2)

        val state = ProgressionState(ex.id, 0, mask)
        val advanced = ProgressionGate.advance(ex, state)
        assertEquals(1, advanced.levelIndex)
        assertEquals(0, advanced.stageMask)
        assertEquals("Elevated Pike Push-up", ex.ladder[advanced.levelIndex].name)
    }

    @Test
    fun topOfTheLadderCannotAdvance() {
        val ex = SeedData.exercise("l_sit")
        val top = ProgressionState(ex.id, ex.ladder.lastIndex, 7)
        assertEquals(top, ProgressionGate.advance(ex, top))
        assertFalse(ProgressionGate.hasNextLevel(ex, ex.ladder.lastIndex))
    }

    @Test
    fun stageTextMatchesTheSpec() {
        val ex = SeedData.exercise("pike_pushups")
        val stages = ProgressionGate.stages(ex, 0)
        assertEquals(listOf("Volume Mastery", "Eccentric Tempo", "Isometric Pause", "Leverage Unlock"), stages.map { it.title })
        assertEquals("Hit 3-4 sets x 12 clean reps with RIR 1-2.", stages[0].description)
        assertEquals("Master a 4-second controlled lowering phase.", stages[1].description)
        assertEquals("Hold a 2-second pause at the hardest mechanical point of the rep.", stages[2].description)
        assertTrue(stages[3].description.contains("Ground Pike Push-up -> Elevated Pike Push-up"))
        val hold = ProgressionGate.stages(SeedData.exercise("l_sit"), 1)
        assertTrue(hold[0].description.contains("20 seconds"))
    }

    @Test
    fun stageOneSuggestionNeedsThreeSetsOfTwelve() {
        val ex = SeedData.exercise("parallel_bar_dips")
        val d = LocalDate.of(2026, 10, 1)
        val good = List(3) { set(d, ex.id, MovementCategory.PUSH, reps = 12, rir = 2) }
        assertTrue(ProgressionGate.stage1Suggested(ex, good))
        val tooHard = List(3) { set(d, ex.id, MovementCategory.PUSH, reps = 12, rir = 0) }
        assertFalse(ProgressionGate.stage1Suggested(ex, tooHard))
        assertFalse(ProgressionGate.stage1Suggested(ex, good.take(2)))
        val holds = SeedData.exercise("l_sit")
        val holdSets = List(3) { set(d, holds.id, MovementCategory.ISOMETRIC, hold = 20) }
        assertTrue(ProgressionGate.stage1Suggested(holds, holdSets))
    }

    @Test
    fun everyExerciseHasStageInfo() {
        for (ex in SeedData.exercises) {
            val stages = ProgressionGate.stages(ex, ex.startLevel)
            assertEquals(4, stages.size)
            assertNotNull(stages[3].description)
        }
    }

    // ------------------------------------------------------------------ charts

    @Test
    fun chartSeriesAggregatePerSession() {
        val d1 = LocalDate.of(2026, 9, 28)
        val d2 = LocalDate.of(2026, 9, 30)
        val sets = listOf(
            set(d1, "l_sit", MovementCategory.ISOMETRIC, hold = 15, session = 1),
            set(d1, "l_sit", MovementCategory.ISOMETRIC, hold = 18, session = 1),
            set(d2, "l_sit", MovementCategory.ISOMETRIC, hold = 20, session = 2),
            set(d1, "planche_lean", MovementCategory.ISOMETRIC, hold = 12, session = 1),
            set(d1, "strict_pullups", MovementCategory.PULL, reps = 8, session = 1),
            set(d1, "strict_pullups", MovementCategory.PULL, reps = 7, session = 1),
            set(d2, "strict_pullups", MovementCategory.PULL, reps = 9, session = 2),
        )
        val lsit = ChartSeries.maxHoldPerSession(sets, "l_sit")
        assertEquals(listOf(18.0, 20.0), lsit.map { it.value })
        assertEquals(listOf(12.0), ChartSeries.maxHoldPerSession(sets, "planche_lean").map { it.value })
        val pulls = ChartSeries.totalRepsPerSession(sets, setOf("strict_pullups"))
        assertEquals(listOf(15.0, 9.0), pulls.map { it.value })
        assertTrue(pulls[0].epochMs < pulls[1].epochMs)
        val dips = ChartSeries.totalRepsPerSession(sets, setOf("parallel_bar_dips", "straight_bar_dips"))
        assertTrue(dips.isEmpty())
        assertNotNull(Instant.ofEpochMilli(lsit[0].epochMs))
    }
}
