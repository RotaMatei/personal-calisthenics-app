package com.personal.calisthenics.core.plan

import com.personal.calisthenics.core.model.WorkoutDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Day rotation: A -> B -> C -> A, driven by the last *completed* session (not the calendar). */
object DayRotation {
    fun next(lastCompleted: WorkoutDay?): WorkoutDay = lastCompleted?.next() ?: WorkoutDay.A
}

/**
 * Deload logic: a 6-week block. Weeks 1-5 are accumulation, week 6 is the deload week in which the total
 * number of sets in Phase 1 and 2 is halved so connective tissue can super-compensate.
 * Weeks are calendar weeks (Monday-Sunday) counted from the Monday of the block start.
 */
object Deload {
    const val ACCUMULATION_WEEKS = 5
    const val BLOCK_LENGTH_WEEKS = ACCUMULATION_WEEKS + 1

    /** Normalizes any start date to the Monday of its week. */
    fun normalizeStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 1-based week within the current block (1..6). Dates before the start count as week 1. */
    fun weekInBlock(blockStart: LocalDate, today: LocalDate): Int {
        val start = normalizeStart(blockStart)
        val days = ChronoUnit.DAYS.between(start, today)
        if (days < 0) return 1
        return ((days / 7) % BLOCK_LENGTH_WEEKS).toInt() + 1
    }

    /** 1-based number of the block the date falls into. */
    fun blockNumber(blockStart: LocalDate, today: LocalDate): Int {
        val start = normalizeStart(blockStart)
        val days = ChronoUnit.DAYS.between(start, today)
        if (days < 0) return 1
        return ((days / 7) / BLOCK_LENGTH_WEEKS).toInt() + 1
    }

    fun isDeloadWeek(blockStart: LocalDate, today: LocalDate): Boolean = weekInBlock(blockStart, today) == BLOCK_LENGTH_WEEKS

    /** Weeks left before the next deload week starts (0 while inside the deload week). */
    fun weeksUntilDeload(blockStart: LocalDate, today: LocalDate): Int {
        val week = weekInBlock(blockStart, today)
        return if (week >= BLOCK_LENGTH_WEEKS) 0 else BLOCK_LENGTH_WEEKS - week
    }

    /**
     * Halves the total set count of the given exercises using the largest-remainder idea:
     * every exercise keeps at least one set, odd counts are rounded up first until the total is
     * about 50% of the original (rounded half up), so 29 planned sets become 15.
     */
    fun deloadSets(planned: List<Int>): List<Int> {
        if (planned.isEmpty()) return emptyList()
        val total = planned.sum()
        val target = maxOf(planned.size, (total + 1) / 2)
        val result = planned.map { maxOf(1, it / 2) }.toMutableList()
        var remaining = target - result.sum()
        // First round the odd-count exercises up, in session order.
        for (i in planned.indices) {
            if (remaining <= 0) break
            if (planned[i] % 2 == 1 && result[i] < planned[i]) {
                result[i] = result[i] + 1
                remaining--
            }
        }
        // Anything left (only possible with even counts) is handed out in order, never above the plan.
        var guard = 0
        while (remaining > 0 && guard < 1000) {
            var progressed = false
            for (i in planned.indices) {
                if (remaining <= 0) break
                if (result[i] < planned[i]) {
                    result[i] = result[i] + 1
                    remaining--
                    progressed = true
                }
            }
            if (!progressed) break
            guard++
        }
        return result
    }
}
