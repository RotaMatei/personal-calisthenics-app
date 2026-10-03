package com.personal.calisthenics.core.analytics

import com.personal.calisthenics.core.model.MovementCategory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeeklyVolume(val weekStart: LocalDate, val pullReps: Int, val pushReps: Int)

data class VolumeCheck(
    val current: WeeklyVolume,
    /** The week the current week is compared with (the previous non-deload week), if any. */
    val baseline: WeeklyVolume?,
    val pullChangePercent: Double?,
    val pushChangePercent: Double?,
    val pullSpike: Boolean,
) {
    val warning: String? get() = if (pullSpike) VolumeGuard.WARNING_MESSAGE else null
}

/**
 * Tendon Safety Guard: if weekly pulling volume rises by more than 15% over the previous week, warn about
 * medial epicondylitis (golfer's elbow). A deload week is skipped as a baseline so returning to normal volume
 * after a deload does not raise a false alarm.
 */
object VolumeGuard {
    const val SPIKE_PERCENT = 15
    const val WARNING_MESSAGE =
        "Medial Epicondylitis (Golfer's Elbow) Risk: Weekly pulling volume spiked by >15%. " +
            "Keep volume steady to allow tendon adaptation."

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Strictly more than 15% above the previous week; a zero baseline never counts as a spike. */
    fun isSpike(current: Int, previous: Int): Boolean = previous > 0 && current.toLong() * 100 > previous.toLong() * (100 + SPIKE_PERCENT)

    fun changePercent(current: Int, previous: Int): Double? =
        if (previous <= 0) null else (current - previous) * 100.0 / previous

    fun weeklyVolumes(sets: List<LoggedSet>, zone: ZoneId): Map<LocalDate, WeeklyVolume> {
        val pull = mutableMapOf<LocalDate, Int>()
        val push = mutableMapOf<LocalDate, Int>()
        for (set in sets) {
            val reps = set.reps ?: continue
            val start = weekStart(Instant.ofEpochMilli(set.epochMs).atZone(zone).toLocalDate())
            when (set.category) {
                MovementCategory.PULL -> pull[start] = (pull[start] ?: 0) + reps
                MovementCategory.PUSH -> push[start] = (push[start] ?: 0) + reps
                else -> Unit
            }
        }
        return (pull.keys + push.keys).associateWith { WeeklyVolume(it, pull[it] ?: 0, push[it] ?: 0) }
    }

    fun evaluate(
        weeks: Map<LocalDate, WeeklyVolume>,
        currentWeekStart: LocalDate,
        isDeloadWeek: (LocalDate) -> Boolean,
    ): VolumeCheck {
        val current = weeks[currentWeekStart] ?: WeeklyVolume(currentWeekStart, 0, 0)
        var baselineStart = currentWeekStart.minusWeeks(1)
        if (isDeloadWeek(baselineStart)) baselineStart = baselineStart.minusWeeks(1)
        val baseline = weeks[baselineStart]
        val pullChange = baseline?.let { changePercent(current.pullReps, it.pullReps) }
        val pushChange = baseline?.let { changePercent(current.pushReps, it.pushReps) }
        val spike = baseline != null && isSpike(current.pullReps, baseline.pullReps)
        return VolumeCheck(current, baseline, pullChange, pushChange, spike)
    }
}
