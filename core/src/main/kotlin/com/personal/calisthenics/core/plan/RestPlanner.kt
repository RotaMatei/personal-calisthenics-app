package com.personal.calisthenics.core.plan

/**
 * Smart rest: heavy compound lifts get 120-180 s depending on how hard the set was (reps in reserve),
 * isometrics and core use a fixed 90 s, and Cold Weather / Superset mode caps every rest at 90 s so
 * the body stays warm outdoors.
 */
object RestPlanner {
    const val COLD_MODE_REST_SEC = 90

    /**
     * @param rir reps in reserve of the set just logged (null for holds or when not logged)
     * @param coldCapSec when set, the result never exceeds this many seconds
     */
    fun restSeconds(restMinSec: Int, restMaxSec: Int, rir: Int?, coldCapSec: Int? = null): Int {
        val lo = minOf(restMinSec, restMaxSec)
        val hi = maxOf(restMinSec, restMaxSec)
        val base = when {
            lo == hi -> hi
            rir == null || rir <= 0 -> hi
            rir == 1 -> roundToFive((lo + hi) / 2.0)
            else -> lo
        }
        return if (coldCapSec != null) minOf(base, coldCapSec) else base
    }

    private fun roundToFive(value: Double): Int = (Math.round(value / 5.0) * 5).toInt()
}
