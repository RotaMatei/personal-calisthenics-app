package com.personal.calisthenics.core.plan

import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind

/** One exercise of the workout as the details page lists it. */
data class OutlineRow(
    val stepId: String,
    val exerciseId: String,
    val number: Int?,
    val title: String,
    val kind: StepKind,
    /** What to do: "3 x 8-10 reps", "2 x 20-30 s hold", "45 s". Per-side work says so. */
    val prescription: String,
    /** Rest between sets in minutes ("1.5-2 min"); null for drills that run back to back. */
    val rest: String?,
    /** "3-1-X-1" style cadence for rep sets, else null. */
    val tempo: String?,
    val cue: String,
    /** Extra facts: superset partner, deload change, per-side. */
    val notes: List<String>,
    /** How each drill of a warm-up or stretch goes (one line per part); empty for sets. */
    val details: List<String> = emptyList(),
)

data class OutlineSection(
    val phase: Phase,
    val minutesLabel: String,
    /** One line about how this phase runs (timers, transitions), or null. */
    val note: String?,
    val rows: List<OutlineRow>,
)

/** Turns a [SessionPlan] into the rows of the workout details page: exact sets, reps or seconds, and rest in minutes. */
object WorkoutOutline {

    fun of(plan: SessionPlan): List<OutlineSection> {
        val coldMode = plan.options.coldMode
        val titles = plan.steps.associate { it.stepId to it.title }
        return Phase.entries.mapNotNull { phase ->
            val steps = plan.steps.filter { it.phase == phase }
            if (steps.isEmpty()) return@mapNotNull null
            OutlineSection(
                phase = phase,
                minutesLabel = phase.minutesLabel,
                note = when (phase) {
                    Phase.WARMUP, Phase.DECOMPRESSION ->
                        "Runs on its own timer, ${SessionPlan.FLOW_TRANSITION_SEC} s get-ready before each drill."
                    Phase.ISOMETRICS -> "Each hold starts with a ${SessionPlan.ISOMETRIC_GET_READY_SEC} s get-ready."
                    Phase.STRENGTH -> if (coldMode) "Paired exercises alternate set by set with a short rest." else null
                },
                rows = steps.map { row(it, coldMode, titles) },
            )
        }
    }

    private fun row(step: PlannedStep, coldMode: Boolean, titles: Map<String, String>): OutlineRow {
        val notes = mutableListOf<String>()
        if (step.sets != step.originalSets && step.kind != StepKind.FLOW) {
            notes += "Deload week: ${step.sets} set${plural(step.sets)} instead of ${step.originalSets}"
        }
        if (step.perSide && step.kind != StepKind.FLOW) notes += "Each side or leg counts as its own set"
        val partner = if (coldMode) step.partnerStepId?.let { titles[it] } else null
        if (partner != null) notes += "Superset with $partner"

        val cap = if (partner != null) RestPlanner.COLD_MODE_REST_SEC else null
        val rest = when (step.kind) {
            StepKind.FLOW -> null
            else -> restRange(step.restMinSec, step.restMaxSec, cap)
        }
        return OutlineRow(
            stepId = step.stepId,
            exerciseId = step.exerciseId,
            number = step.number,
            title = step.title,
            kind = step.kind,
            prescription = prescription(step),
            rest = rest,
            tempo = step.tempo?.notation,
            cue = step.cue,
            notes = notes,
            details = drillDetails(step),
        )
    }

    private fun drillDetails(step: PlannedStep): List<String> = when {
        step.kind != StepKind.FLOW -> emptyList()
        step.flowItems.size == 1 -> listOf(step.flowItems[0].instruction)
        else -> step.flowItems.map { "${it.label} (${duration(it.seconds)}): ${it.instruction}" }
    }

    private fun prescription(step: PlannedStep): String = when (step.kind) {
        StepKind.FLOW -> flowPrescription(step.flowItems.map { it.seconds })
        StepKind.ISOMETRIC -> {
            val lo = step.holdMinSec ?: step.holdMaxSec ?: 0
            val hi = step.holdMaxSec ?: lo
            "${step.sets} x ${range(lo, hi)} s hold"
        }
        StepKind.STRENGTH -> {
            val lo = step.repsMin ?: step.repsMax ?: 0
            val hi = step.repsMax ?: lo
            "${step.sets} x ${range(lo, hi)} reps"
        }
    }

    private fun flowPrescription(seconds: List<Int>): String = when {
        seconds.isEmpty() -> ""
        seconds.size == 1 -> duration(seconds[0])
        seconds.distinct().size == 1 -> "${seconds.size} x ${duration(seconds[0])}"
        else -> duration(seconds.sum()) + " in total"
    }

    private fun range(lo: Int, hi: Int): String = if (lo == hi) "$hi" else "$lo-$hi"

    private fun plural(n: Int) = if (n == 1) "" else "s"

    /** "45 s", "2 min", "1 min 30 s". */
    fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60} min ${seconds % 60} s"
    }

    /** Minutes as people say them: 90 s -> "1.5", 120 s -> "2", 75 s -> "1.25". */
    fun minutes(seconds: Int): String {
        val m = seconds / 60.0
        val text = String.format(java.util.Locale.ENGLISH, "%.2f", m).trimEnd('0').trimEnd('.')
        return text
    }

    /** "2 min" or "1.5-2.5 min"; [capSec] limits both ends (cold-weather supersets). */
    fun restRange(minSec: Int, maxSec: Int, capSec: Int? = null): String {
        var lo = minOf(minSec, maxSec)
        var hi = maxOf(minSec, maxSec)
        if (capSec != null) { lo = minOf(lo, capSec); hi = minOf(hi, capSec) }
        return if (lo == hi) "${minutes(hi)} min" else "${minutes(lo)}-${minutes(hi)} min"
    }
}
