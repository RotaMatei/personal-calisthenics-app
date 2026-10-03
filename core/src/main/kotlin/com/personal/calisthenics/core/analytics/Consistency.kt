package com.personal.calisthenics.core.analytics

import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.Deload
import java.time.LocalDate
import java.time.YearMonth

object Streaks {
    const val WEEKLY_TARGET = 3

    /**
     * Number of consecutive calendar weeks (Mon-Sun) with at least [target] completed sessions.
     * The current week does not break the streak while it is still in progress.
     */
    fun weeklyStreak(sessionDates: List<LocalDate>, today: LocalDate, target: Int = WEEKLY_TARGET): Int {
        if (sessionDates.isEmpty()) return 0
        val perWeek = sessionDates.groupingBy { VolumeGuard.weekStart(it) }.eachCount()
        var cursor = VolumeGuard.weekStart(today)
        if ((perWeek[cursor] ?: 0) < target) cursor = cursor.minusWeeks(1)
        var streak = 0
        while ((perWeek[cursor] ?: 0) >= target) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }
}

enum class CellKind { WORKOUT_A, WORKOUT_B, WORKOUT_C, REST, FUTURE }

data class HeatmapCell(
    val date: LocalDate,
    val kind: CellKind,
    val deloadWeek: Boolean,
    val isToday: Boolean,
)

/** weeks[w][d]: week w (Monday first), weekday d (Monday = 0). Null pads days outside the month. */
data class HeatmapMonth(val month: YearMonth, val weeks: List<List<HeatmapCell?>>, val workoutCount: Int)

/** GitHub-style monthly contribution grid. */
object Heatmap {
    fun month(
        month: YearMonth,
        sessionDays: Map<LocalDate, WorkoutDay>,
        blockStart: LocalDate,
        today: LocalDate,
    ): HeatmapMonth {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        var cursor = VolumeGuard.weekStart(first)
        val weeks = mutableListOf<List<HeatmapCell?>>()
        var workouts = 0
        while (!cursor.isAfter(last)) {
            val week = (0 until 7).map { offset ->
                val date = cursor.plusDays(offset.toLong())
                if (date.isBefore(first) || date.isAfter(last)) {
                    null
                } else {
                    val day = sessionDays[date]
                    val kind = when {
                        day == WorkoutDay.A -> CellKind.WORKOUT_A
                        day == WorkoutDay.B -> CellKind.WORKOUT_B
                        day == WorkoutDay.C -> CellKind.WORKOUT_C
                        date.isAfter(today) -> CellKind.FUTURE
                        else -> CellKind.REST
                    }
                    if (day != null) workouts++
                    HeatmapCell(date, kind, Deload.isDeloadWeek(blockStart, date), date == today)
                }
            }
            weeks += week
            cursor = cursor.plusWeeks(1)
        }
        return HeatmapMonth(month, weeks, workouts)
    }
}
