package com.personal.calisthenics.core

import com.personal.calisthenics.core.analytics.CompletedSession
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.analytics.RecoveryState
import com.personal.calisthenics.core.dashboard.DashboardLogic
import com.personal.calisthenics.core.dashboard.PreWorkoutChecklist
import com.personal.calisthenics.core.model.Grip
import com.personal.calisthenics.core.model.WorkoutDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardTest {
    private val hour = 3_600_000L
    private val today = LocalDate.of(2026, 10, 7) // Wednesday
    private val blockStart = LocalDate.of(2026, 10, 5)

    private fun session(day: WorkoutDay, endedAt: Long) = CompletedSession(1, endedAt - hour, endedAt, day, false)

    @Test
    fun firstLaunchStartsWithDayAAndNoRecoveryHistory() {
        val m = DashboardLogic.build(null, null, emptyList(), blockStart, today, 10 * hour)
        assertEquals(WorkoutDay.A, m.nextDay)
        assertEquals(RecoveryState.NO_HISTORY, m.recovery.state)
        assertEquals(Grip.OVERHAND, m.grip)
        assertFalse(m.deloadWeek)
    }

    @Test
    fun rotationFollowsTheLastCompletedSession() {
        val m = DashboardLogic.build(session(WorkoutDay.B, 100 * hour), null, listOf(today), blockStart, today, 150 * hour)
        assertEquals(WorkoutDay.C, m.nextDay)
        assertEquals(RecoveryState.OPTIMAL_WINDOW, m.recovery.state)
        assertEquals(Grip.CHIN, m.grip)
    }

    @Test
    fun elbowStiffnessSwitchesTheNextPullToNeutralGrip() {
        val m = DashboardLogic.build(session(WorkoutDay.B, 100 * hour), JointRating(1, 4, 1), emptyList(), blockStart, today, 150 * hour)
        assertEquals(WorkoutDay.C, m.nextDay)
        assertEquals(Grip.NEUTRAL, m.grip)
        assertTrue(m.gripOverriddenByElbow)
        assertTrue(m.advice.messages.isNotEmpty())
    }

    @Test
    fun sixthWeekIsDeloadAndHalvesTheSets() {
        val week6 = blockStart.plusWeeks(5).plusDays(1)
        val normal = DashboardLogic.build(null, null, emptyList(), blockStart, today, 0L)
        val deload = DashboardLogic.build(null, null, emptyList(), blockStart, week6, 0L)
        assertTrue(deload.deloadWeek)
        assertEquals(0, deload.weeksUntilDeload)
        assertTrue(deload.plan.workItems.size < normal.plan.workItems.size)
    }

    @Test
    fun checklistHasThreeNutritionAndThreeGearItemsWithUniqueIds() {
        assertEquals(6, PreWorkoutChecklist.items.size)
        assertEquals(6, PreWorkoutChecklist.items.map { it.id }.toSet().size)
        assertEquals(2, PreWorkoutChecklist.groups.size)
    }
}
