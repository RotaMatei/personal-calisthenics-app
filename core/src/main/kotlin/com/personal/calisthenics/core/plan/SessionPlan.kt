package com.personal.calisthenics.core.plan

import com.personal.calisthenics.core.model.Grip
import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.model.Tempo
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.model.WorkoutStep
import com.personal.calisthenics.core.seed.SeedData

enum class Side(val label: String) { LEFT("Left leg"), RIGHT("Right leg") }

/** Everything the planner needs to build today's session. */
data class PlanOptions(
    val day: WorkoutDay,
    val deload: Boolean = false,
    val coldMode: Boolean = false,
    /** Grip for the pull-up slot; defaults to the grip of [day]. The elbow-stiffness rule overrides this. */
    val pullGrip: Grip = day.pullGrip,
)

/** A drill in a flow list (warm-up or decompression). */
data class FlowItem(
    val stepId: String,
    val exerciseId: String,
    val label: String,
    val seconds: Int,
    val instruction: String,
    val subLabels: List<String>,
)

/** A step after day variants, grip override and deload have been applied. */
data class PlannedStep(
    val stepId: String,
    val number: Int?,
    val phase: Phase,
    val kind: StepKind,
    val exerciseId: String,
    val title: String,
    val sets: Int,
    val originalSets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val holdMinSec: Int?,
    val holdMaxSec: Int?,
    val tempo: Tempo?,
    val restMinSec: Int,
    val restMaxSec: Int,
    val perSide: Boolean,
    val cue: String,
    val partnerStepId: String?,
    val flowItems: List<FlowItem>,
)

/** One logged-able unit of work: a single set (one leg of a set for per-side steps). */
data class WorkItem(
    val stepId: String,
    val exerciseId: String,
    val title: String,
    val kind: StepKind,
    val setNumber: Int,
    val totalSets: Int,
    val side: Side?,
    val repsMin: Int?,
    val repsMax: Int?,
    val holdMinSec: Int?,
    val holdMaxSec: Int?,
    val tempo: Tempo?,
    val restMinSec: Int,
    val restMaxSec: Int,
    val cue: String,
    val number: Int?,
    /** Title of the exercise this one is paired with in superset mode, if any. */
    val supersetWith: String?,
) {
    val key: String get() = "$stepId#$setNumber${side?.let { "#" + it.name } ?: ""}"
}

sealed interface SessionBlock {
    val phase: Phase
}

data class FlowBlock(override val phase: Phase, val items: List<FlowItem>) : SessionBlock {
    val totalSeconds: Int get() = items.sumOf { it.seconds }
}

data class SetBlock(override val phase: Phase, val items: List<WorkItem>) : SessionBlock

data class SessionPlan(
    val options: PlanOptions,
    val steps: List<PlannedStep>,
    val blocks: List<SessionBlock>,
) {
    val day: WorkoutDay get() = options.day

    /** All set items of the session in execution order. */
    val workItems: List<WorkItem> get() = blocks.filterIsInstance<SetBlock>().flatMap { it.items }

    /** Rough duration range in minutes: shortest rests vs longest rests. */
    fun estimatedMinutes(): IntRange {
        val low = estimateSeconds(useMaxRest = false)
        val high = estimateSeconds(useMaxRest = true)
        return ((low + 30) / 60)..((high + 30) / 60)
    }

    private fun estimateSeconds(useMaxRest: Boolean): Int {
        var total = 0
        for (block in blocks) {
            when (block) {
                is FlowBlock -> total += block.items.sumOf { it.seconds } + FLOW_TRANSITION_SEC * block.items.size
                is SetBlock -> block.items.forEachIndexed { index, item ->
                    total += workSeconds(item)
                    val isLastOfSession = block.phase == Phase.STRENGTH && index == block.items.lastIndex
                    if (!isLastOfSession) {
                        val cap = if (options.coldMode && item.supersetWith != null) RestPlanner.COLD_MODE_REST_SEC else null
                        val rest = if (useMaxRest) item.restMaxSec else item.restMinSec
                        total += if (cap != null) minOf(rest, cap) else rest
                    }
                }
            }
        }
        return total
    }

    private fun workSeconds(item: WorkItem): Int = when (item.kind) {
        StepKind.ISOMETRIC -> ISOMETRIC_GET_READY_SEC + (item.holdMaxSec ?: 20)
        StepKind.STRENGTH -> {
            val reps = ((item.repsMin ?: 8) + (item.repsMax ?: 8)) / 2
            reps * (item.tempo?.repSeconds ?: 4) + STRENGTH_SETUP_SEC
        }
        StepKind.FLOW -> 0
    }

    companion object {
        const val FLOW_TRANSITION_SEC = 10
        const val ISOMETRIC_GET_READY_SEC = 5
        const val STRENGTH_SETUP_SEC = 10
    }
}

object SessionPlanner {

    private const val PULLUP_STEP_ID = "p2_pullups"

    fun plan(options: PlanOptions): SessionPlan {
        val day = options.day
        val allSteps = SeedData.steps

        // Deload halves total sets across Phase 1 and 2 only; warm-up and decompression stay complete.
        val loaded = allSteps.filter { it.phase == Phase.ISOMETRICS || it.phase == Phase.STRENGTH }
        val setsByStep: Map<String, Int> = if (options.deload) {
            val reduced = Deload.deloadSets(loaded.map { it.sets })
            loaded.indices.associate { loaded[it].id to reduced[it] }
        } else {
            loaded.associate { it.id to it.sets }
        }

        val planned = allSteps.map { step -> toPlanned(step, options, setsByStep[step.id] ?: step.sets) }
        val blocks = buildBlocks(planned, options)
        return SessionPlan(options, planned, blocks)
    }

    fun pullUpTitle(grip: Grip): String = when (grip) {
        Grip.OVERHAND -> "Strict Pull-ups (Overhand)"
        Grip.NEUTRAL -> "Strict Pull-ups (Neutral Grip)"
        Grip.CHIN -> "Chin-ups (Supinated)"
    }

    private fun toPlanned(step: WorkoutStep, options: PlanOptions, sets: Int): PlannedStep {
        val title = if (step.id == PULLUP_STEP_ID) pullUpTitle(options.pullGrip) else step.titleFor(options.day)
        val exerciseId = step.exerciseFor(options.day)
        val flowItems = if (step.kind == StepKind.FLOW) {
            step.flowParts.map { part ->
                FlowItem(step.id, exerciseId, part.label, part.seconds, part.instruction, part.subLabels)
            }
        } else {
            emptyList()
        }
        return PlannedStep(
            stepId = step.id,
            number = step.number,
            phase = step.phase,
            kind = step.kind,
            exerciseId = exerciseId,
            title = title,
            sets = sets,
            originalSets = step.sets,
            repsMin = step.repsMin,
            repsMax = step.repsMax,
            holdMinSec = step.holdMinSec,
            holdMaxSec = step.holdMaxSec,
            tempo = step.tempo,
            restMinSec = step.restMinSec,
            restMaxSec = step.restMaxSec,
            perSide = step.perSide,
            cue = step.cue,
            partnerStepId = step.partnerStepId,
            flowItems = flowItems,
        )
    }

    private fun buildBlocks(steps: List<PlannedStep>, options: PlanOptions): List<SessionBlock> {
        val blocks = mutableListOf<SessionBlock>()
        for (phase in Phase.entries) {
            val inPhase = steps.filter { it.phase == phase }
            if (inPhase.isEmpty()) continue
            when (phase) {
                Phase.WARMUP, Phase.DECOMPRESSION ->
                    blocks += FlowBlock(phase, inPhase.flatMap { it.flowItems })
                Phase.ISOMETRICS, Phase.STRENGTH ->
                    blocks += SetBlock(phase, sequenceSets(inPhase, options.coldMode))
            }
        }
        return blocks
    }

    /**
     * Orders the sets of a phase. Normally all sets of a step run back to back. In Cold Weather /
     * Antagonist Superset mode the partner steps (3 with 4, 5 with 6) alternate set by set.
     */
    private fun sequenceSets(steps: List<PlannedStep>, coldMode: Boolean): List<WorkItem> {
        val byId = steps.associateBy { it.stepId }
        val consumed = mutableSetOf<String>()
        val items = mutableListOf<WorkItem>()
        for (step in steps) {
            if (step.stepId in consumed) continue
            val partner = if (coldMode) step.partnerStepId?.let { byId[it] } else null
            if (partner != null && partner.stepId !in consumed) {
                consumed += step.stepId
                consumed += partner.stepId
                val rounds = maxOf(step.sets, partner.sets)
                for (round in 1..rounds) {
                    if (round <= step.sets) items += itemsFor(step, round, partner.title)
                    if (round <= partner.sets) items += itemsFor(partner, round, step.title)
                }
            } else {
                consumed += step.stepId
                for (setNumber in 1..step.sets) items += itemsFor(step, setNumber, null)
            }
        }
        return items
    }

    private fun itemsFor(step: PlannedStep, setNumber: Int, partnerTitle: String?): List<WorkItem> {
        val sides: List<Side?> = if (step.perSide) listOf(Side.LEFT, Side.RIGHT) else listOf(null)
        return sides.map { side ->
            WorkItem(
                stepId = step.stepId,
                exerciseId = step.exerciseId,
                title = step.title,
                kind = step.kind,
                setNumber = setNumber,
                totalSets = step.sets,
                side = side,
                repsMin = step.repsMin,
                repsMax = step.repsMax,
                holdMinSec = step.holdMinSec,
                holdMaxSec = step.holdMaxSec,
                tempo = step.tempo,
                restMinSec = step.restMinSec,
                restMaxSec = step.restMaxSec,
                cue = step.cue,
                number = step.number,
                supersetWith = partnerTitle,
            )
        }
    }
}
