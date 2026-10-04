package com.personal.calisthenics.core.dashboard

import com.personal.calisthenics.core.analytics.CompletedSession
import com.personal.calisthenics.core.analytics.JointAdvice
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.analytics.NextSessionAdvice
import com.personal.calisthenics.core.analytics.Recovery
import com.personal.calisthenics.core.analytics.RecoveryStatus
import com.personal.calisthenics.core.analytics.VolumeGuard
import com.personal.calisthenics.core.model.Grip
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenics.core.plan.DayRotation
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.plan.SessionPlan
import com.personal.calisthenics.core.plan.SessionPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the Dashboard tab shows, derived from the session history. */
data class DashboardModel(
    val recovery: RecoveryStatus,
    val nextDay: WorkoutDay,
    /** Grip the pull-up slot will use (the elbow rule can override the day's normal grip). */
    val grip: Grip,
    val gripOverriddenByElbow: Boolean,
    val advice: NextSessionAdvice,
    val weekInBlock: Int,
    val blockNumber: Int,
    val deloadWeek: Boolean,
    val weeksUntilDeload: Int,
    val plan: SessionPlan,
    val estimatedMinutes: IntRange,
    val sessionsThisWeek: Int,
)

object DashboardLogic {

    /** Plan options for the next session given the last finished session and its joint ratings. */
    fun nextOptions(
        lastDay: WorkoutDay?,
        lastRating: JointRating?,
        deload: Boolean,
        coldMode: Boolean,
        dayOverride: WorkoutDay? = null,
    ): PlanOptions {
        val day = dayOverride ?: DayRotation.next(lastDay)
        val advice = JointAdvice.evaluate(lastRating)
        return PlanOptions(day, deload = deload, coldMode = coldMode, pullGrip = advice.gripFor(day.pullGrip))
    }

    fun build(
        last: CompletedSession?,
        lastRating: JointRating?,
        sessionDates: List<LocalDate>,
        blockStart: LocalDate,
        today: LocalDate,
        nowMs: Long,
        coldMode: Boolean = false,
    ): DashboardModel {
        val deload = Deload.isDeloadWeek(blockStart, today)
        val options = nextOptions(last?.day, lastRating, deload, coldMode)
        val plan = SessionPlanner.plan(options)
        val advice = JointAdvice.evaluate(lastRating)
        val weekStart = VolumeGuard.weekStart(today)
        return DashboardModel(
            recovery = Recovery.status(last?.endedAtMs, nowMs),
            nextDay = options.day,
            grip = options.pullGrip,
            gripOverriddenByElbow = advice.forceNeutralGrip && options.day.pullGrip != Grip.NEUTRAL,
            advice = advice,
            weekInBlock = Deload.weekInBlock(blockStart, today),
            blockNumber = Deload.blockNumber(blockStart, today),
            deloadWeek = deload,
            weeksUntilDeload = Deload.weeksUntilDeload(blockStart, today),
            plan = plan,
            estimatedMinutes = plan.estimatedMinutes(),
            sessionsThisWeek = sessionDates.count { VolumeGuard.weekStart(it) == weekStart },
        )
    }

    fun toDate(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}

/** The pre-workout checklist of the Dashboard (45 minutes before training). */
data class ChecklistItem(val id: String, val group: String, val title: String, val detail: String)

object PreWorkoutChecklist {
    const val NUTRITION = "Tendon nutrition protocol"
    const val GEAR = "Park gear check"

    val items: List<ChecklistItem> = listOf(
        ChecklistItem("collagen", NUTRITION, "Hydrolyzed collagen", "10-15 g, about 45 minutes before training"),
        ChecklistItem("vitamin_c", NUTRITION, "Vitamin C", "50-100 mg taken together with the collagen"),
        ChecklistItem("hydration", NUTRITION, "Hydration and creatine", "Drink water; take your creatine if you use it"),
        ChecklistItem("band", GEAR, "Light resistance band", "For dislocates and band pull-aparts"),
        ChecklistItem("chalk", GEAR, "Chalk", "Liquid or block"),
        ChecklistItem("wraps", GEAR, "Neoprene wrist wraps, elbow sleeves, long sleeves", "Pack them when it is cold"),
    )

    val groups: List<String> = items.map { it.group }.distinct()
}
