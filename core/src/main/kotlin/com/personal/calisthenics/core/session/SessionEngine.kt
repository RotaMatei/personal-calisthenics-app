package com.personal.calisthenics.core.session

import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.plan.FlowBlock
import com.personal.calisthenics.core.plan.FlowItem
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.plan.RestPlanner
import com.personal.calisthenics.core.plan.SessionPlan
import com.personal.calisthenics.core.plan.SessionPlanner
import com.personal.calisthenics.core.plan.SetBlock
import com.personal.calisthenics.core.plan.WorkItem
import com.personal.calisthenics.core.timer.CueType
import com.personal.calisthenics.core.timer.PhaseKind
import com.personal.calisthenics.core.timer.TimelineTimer
import com.personal.calisthenics.core.timer.TimerSnapshot
import com.personal.calisthenics.core.timer.Timers

/** What the athlete entered (or accepted) when a set ended. */
data class SetResult(
    val reps: Int?,
    val holdSeconds: Int?,
    val rir: Int?,
    val levelIndex: Int,
)

/** A set that has been logged, in the order it happened. */
data class LoggedEntry(val item: WorkItem, val result: SetResult, val atMs: Long)

/** Where the session currently is. */
enum class StageKind {
    NOT_STARTED,
    /** Warm-up or decompression block waiting for the Start tap. */
    FLOW_READY,
    /** A flow timer is running (drills with 10 s setup transitions). */
    FLOW,
    /** Next work item shown, waiting for Start. */
    READY,
    /** Isometric get-ready + hold, or the tempo metronome, is running. */
    ACTIVE,
    /** The set is over; waiting for the athlete to confirm reps / hold / RIR / level. */
    LOGGING,
    REST,
    /** All blocks done; waiting for the post-workout joint ratings. */
    JOINT_LOG,
    FINISHED,
}

/** Immutable picture of the engine for the UI. */
data class EngineSnapshot(
    val stage: StageKind,
    val blockIndex: Int,
    val phase: Phase?,
    /** The set being prepared / performed / logged, or the set just finished while resting. */
    val item: WorkItem?,
    /** The set that comes after the current rest, or the next one in the session. */
    val nextItem: WorkItem?,
    val flowItems: List<FlowItem>,
    val timer: TimerSnapshot?,
    val paused: Boolean,
    val completedSets: Int,
    val totalSets: Int,
    /** Reps the metronome plans for this set (strength) or null. */
    val targetReps: Int?,
    /** Suggested values for the log dialog. */
    val suggestedReps: Int?,
    val suggestedHold: Int?,
    val restSeconds: Int?,
    val coldMode: Boolean,
    val coldModeChangeable: Boolean,
)

/**
 * The workout player's brain: walks through the blocks of a [SessionPlan] (flow lists and logged sets), runs the
 * right timer for each part, applies the smart rest rules and records every logged set. It has no Android
 * dependency and no clock of its own: every call receives the current monotonic time in milliseconds.
 */
class SessionEngine(initialPlan: SessionPlan) {

    var plan: SessionPlan = initialPlan
        private set

    private sealed interface Stage {
        data object NotStarted : Stage
        data class FlowReady(val block: Int) : Stage
        data class Flow(val block: Int, val timer: TimelineTimer, val items: List<FlowItem>) : Stage
        data class Ready(val block: Int, val index: Int) : Stage
        data class Active(val block: Int, val index: Int, val timer: TimelineTimer, val targetReps: Int?) : Stage
        data class Logging(val block: Int, val index: Int, val suggestedReps: Int?, val suggestedHold: Int?) : Stage
        data class Rest(val block: Int, val index: Int, val timer: TimelineTimer, val seconds: Int) : Stage
        data object JointLog : Stage
        data object Finished : Stage
    }

    private var stage: Stage = Stage.NotStarted
    private val logged = mutableListOf<LoggedEntry>()
    private var jointRating: JointRating? = null
    private var startedMs: Long = 0L

    val entries: List<LoggedEntry> get() = logged
    val rating: JointRating? get() = jointRating

    val totalSets: Int get() = plan.workItems.size

    // ------------------------------------------------------------------ controls

    /** Leaves the intro and enters the first block. */
    fun begin(nowMs: Long) {
        if (stage !is Stage.NotStarted) return
        startedMs = nowMs
        enterBlock(0)
    }

    /** Starts the flow timer of the block that is waiting at [StageKind.FLOW_READY]. */
    fun startFlow(nowMs: Long) {
        val s = stage as? Stage.FlowReady ?: return
        val block = plan.blocks[s.block] as FlowBlock
        val timer = Timers.flow(block.items)
        timer.start(nowMs)
        stage = Stage.Flow(s.block, timer, block.items)
    }

    /** Starts the isometric get-ready + hold or the tempo metronome for the waiting set. */
    fun startSet(nowMs: Long) {
        val s = stage as? Stage.Ready ?: return
        val item = itemAt(s.block, s.index)
        val timer: TimelineTimer
        var targetReps: Int? = null
        when {
            item.kind == StepKind.ISOMETRIC && item.holdMinSec != null && item.holdMaxSec != null ->
                timer = Timers.isometric(item.holdMinSec, item.holdMaxSec)
            item.tempo != null -> {
                targetReps = (item.repsMax ?: item.repsMin ?: 8).coerceAtLeast(1)
                timer = Timers.tempo(item.tempo, targetReps)
            }
            else -> {
                // No timer applies (should not happen with the seeded plan): go straight to logging.
                stage = Stage.Logging(s.block, s.index, item.repsMax, item.holdMaxSec)
                return
            }
        }
        timer.start(nowMs)
        stage = Stage.Active(s.block, s.index, timer, targetReps)
    }

    /** Ends the running set early; reps / hold are taken from how far the timer got. */
    fun stopSet(nowMs: Long) {
        val s = stage as? Stage.Active ?: return
        val snap = s.timer.snapshot(nowMs)
        val phase = snap.phase
        var reps: Int? = null
        var hold: Int? = null
        if (s.targetReps != null) {
            val rep = phase?.rep ?: 0
            reps = when (phase?.kind) {
                PhaseKind.PAUSE_TOP -> rep
                PhaseKind.LOWER, PhaseKind.PAUSE_BOTTOM, PhaseKind.DRIVE -> rep - 1
                else -> if (snap.finished) s.targetReps else 0
            }.coerceIn(0, s.targetReps)
        } else {
            hold = if (phase?.kind == PhaseKind.HOLD) ((snap.phaseElapsedMs + 500L) / 1000L).toInt() else if (snap.finished) itemAt(s.block, s.index).holdMaxSec else 0
        }
        stage = Stage.Logging(s.block, s.index, reps, hold)
    }

    /** Skips the waiting set without logging it. */
    fun skipSet(nowMs: Long) {
        val s = stage as? Stage.Ready ?: return
        advanceFrom(s.block, s.index, itemAt(s.block, s.index), rir = null, nowMs = nowMs, startRest = false)
    }

    /** Records the finished set and starts the smart rest (or moves on when no rest is due). */
    fun logSet(result: SetResult, nowMs: Long) {
        val s = stage as? Stage.Logging ?: return
        val item = itemAt(s.block, s.index)
        logged += LoggedEntry(item, result, nowMs)
        advanceFrom(s.block, s.index, item, result.rir, nowMs, startRest = true)
    }

    /** Ends the rest right now. */
    fun skipRest(nowMs: Long) {
        val s = stage as? Stage.Rest ?: return
        enterNextAfter(s.block, s.index)
    }

    /** Adds [seconds] to the running rest (for example when the previous set was harder than planned). */
    fun extendRest(seconds: Int, nowMs: Long) {
        val s = stage as? Stage.Rest ?: return
        val remaining = s.timer.snapshot(nowMs).phaseRemainingMs / 1000L
        val timer = Timers.rest((remaining + seconds).toInt().coerceAtLeast(1))
        timer.start(nowMs)
        stage = Stage.Rest(s.block, s.index, timer, (remaining + seconds).toInt())
    }

    fun pause(nowMs: Long) {
        activeTimer()?.pause(nowMs)
    }

    fun resume(nowMs: Long) {
        activeTimer()?.resume(nowMs)
    }

    /** Flow only: jump to the next drill (or its setup transition). */
    fun skipFlowPhase(nowMs: Long) {
        (stage as? Stage.Flow)?.timer?.skipToNextPhase(nowMs)
    }

    fun previousFlowPhase(nowMs: Long) {
        (stage as? Stage.Flow)?.timer?.skipToPreviousPhase(nowMs)
    }

    /** Stops whatever is running and goes to the joint log (what was logged so far still counts). */
    fun endEarly() {
        if (stage is Stage.JointLog || stage is Stage.Finished) return
        stage = Stage.JointLog
    }

    /** Completes the session with optional joint ratings. */
    fun finish(rating: JointRating?) {
        if (stage !is Stage.JointLog) return
        jointRating = rating
        stage = Stage.Finished
    }

    /**
     * Cold Weather / Antagonist Supersets can only change before the first set of the session is logged,
     * because it re-orders the sets of Phases 1 and 2.
     */
    val coldModeChangeable: Boolean
        get() = logged.isEmpty() && (stage is Stage.NotStarted || stage is Stage.FlowReady || stage is Stage.Flow || stage is Stage.Ready)

    fun setColdMode(on: Boolean): Boolean {
        if (!coldModeChangeable || plan.options.coldMode == on) return plan.options.coldMode == on
        plan = SessionPlanner.plan(plan.options.copy(coldMode = on))
        val s = stage
        if (s is Stage.Ready) stage = Stage.Ready(s.block, 0)
        return true
    }

    // ------------------------------------------------------------------ time

    /** Advances the engine to [nowMs]: returns the cues that fired and performs automatic stage changes. */
    fun tick(nowMs: Long): List<CueType> {
        val cues = mutableListOf<CueType>()
        when (val s = stage) {
            is Stage.Flow -> {
                cues += s.timer.poll(nowMs).map { it.type }
                if (s.timer.isFinished) enterBlock(s.block + 1)
            }
            is Stage.Active -> {
                cues += s.timer.poll(nowMs).map { it.type }
                if (s.timer.isFinished) {
                    val item = itemAt(s.block, s.index)
                    stage = Stage.Logging(s.block, s.index, s.targetReps, if (s.targetReps == null) item.holdMaxSec else null)
                }
            }
            is Stage.Rest -> {
                cues += s.timer.poll(nowMs).map { it.type }
                if (s.timer.isFinished) enterNextAfter(s.block, s.index)
            }
            else -> Unit
        }
        return cues
    }

    fun snapshot(nowMs: Long): EngineSnapshot {
        val s = stage
        val kind: StageKind
        var block = 0
        var item: WorkItem? = null
        var next: WorkItem? = null
        var flow: List<FlowItem> = emptyList()
        var timer: TimerSnapshot? = null
        var paused = false
        var targetReps: Int? = null
        var sugReps: Int? = null
        var sugHold: Int? = null
        var restSec: Int? = null
        when (s) {
            Stage.NotStarted -> kind = StageKind.NOT_STARTED
            is Stage.FlowReady -> { kind = StageKind.FLOW_READY; block = s.block; flow = (plan.blocks[s.block] as FlowBlock).items }
            is Stage.Flow -> {
                kind = StageKind.FLOW; block = s.block; flow = s.items
                timer = s.timer.snapshot(nowMs); paused = !s.timer.isRunning && !s.timer.isFinished
            }
            is Stage.Ready -> { kind = StageKind.READY; block = s.block; item = itemAt(s.block, s.index); next = nextAfter(s.block, s.index) }
            is Stage.Active -> {
                kind = StageKind.ACTIVE; block = s.block; item = itemAt(s.block, s.index); next = nextAfter(s.block, s.index)
                timer = s.timer.snapshot(nowMs); paused = !s.timer.isRunning && !s.timer.isFinished; targetReps = s.targetReps
            }
            is Stage.Logging -> {
                kind = StageKind.LOGGING; block = s.block; item = itemAt(s.block, s.index); next = nextAfter(s.block, s.index)
                sugReps = s.suggestedReps; sugHold = s.suggestedHold
            }
            is Stage.Rest -> {
                kind = StageKind.REST; block = s.block; item = itemAt(s.block, s.index); next = nextAfter(s.block, s.index)
                timer = s.timer.snapshot(nowMs); paused = !s.timer.isRunning && !s.timer.isFinished; restSec = s.seconds
            }
            Stage.JointLog -> kind = StageKind.JOINT_LOG
            Stage.Finished -> kind = StageKind.FINISHED
        }
        return EngineSnapshot(
            stage = kind,
            blockIndex = block,
            phase = plan.blocks.getOrNull(block)?.phase.takeIf { kind != StageKind.NOT_STARTED && kind != StageKind.JOINT_LOG && kind != StageKind.FINISHED },
            item = item,
            nextItem = next,
            flowItems = flow,
            timer = timer,
            paused = paused,
            completedSets = logged.size,
            totalSets = totalSets,
            targetReps = targetReps,
            suggestedReps = sugReps,
            suggestedHold = sugHold,
            restSeconds = restSec,
            coldMode = plan.options.coldMode,
            coldModeChangeable = coldModeChangeable,
        )
    }

    // ------------------------------------------------------------------ internals

    private fun activeTimer(): TimelineTimer? = when (val s = stage) {
        is Stage.Flow -> s.timer
        is Stage.Active -> s.timer
        is Stage.Rest -> s.timer
        else -> null
    }

    private fun itemAt(block: Int, index: Int): WorkItem = (plan.blocks[block] as SetBlock).items[index]

    /** The set that follows (block, index) in the session, or null after the last one. */
    private fun nextAfter(block: Int, index: Int): WorkItem? {
        val current = plan.blocks[block] as SetBlock
        if (index + 1 < current.items.size) return current.items[index + 1]
        for (b in block + 1 until plan.blocks.size) {
            val candidate = plan.blocks[b]
            if (candidate is SetBlock && candidate.items.isNotEmpty()) return candidate.items.first()
        }
        return null
    }

    private fun enterBlock(block: Int) {
        if (block >= plan.blocks.size) {
            stage = Stage.JointLog
            return
        }
        stage = when (val b = plan.blocks[block]) {
            is FlowBlock -> if (b.items.isEmpty()) { enterBlock(block + 1); return } else Stage.FlowReady(block)
            is SetBlock -> if (b.items.isEmpty()) { enterBlock(block + 1); return } else Stage.Ready(block, 0)
        }
    }

    private fun enterNextAfter(block: Int, index: Int) {
        val current = plan.blocks[block] as SetBlock
        if (index + 1 < current.items.size) stage = Stage.Ready(block, index + 1) else enterBlock(block + 1)
    }

    private fun advanceFrom(block: Int, index: Int, item: WorkItem, rir: Int?, nowMs: Long, startRest: Boolean) {
        val isLastOfSession = item.let {
            val b = plan.blocks[block]
            b.phase == Phase.STRENGTH && index == (b as SetBlock).items.lastIndex
        }
        if (!startRest || isLastOfSession) {
            enterNextAfter(block, index)
            return
        }
        val cap = if (plan.options.coldMode && item.supersetWith != null) RestPlanner.COLD_MODE_REST_SEC else null
        val seconds = RestPlanner.restSeconds(item.restMinSec, item.restMaxSec, rir, cap)
        if (seconds <= 0) {
            enterNextAfter(block, index)
            return
        }
        val timer = Timers.rest(seconds)
        timer.start(nowMs)
        stage = Stage.Rest(block, index, timer, seconds)
    }
}

/** Convenience: build an engine for [options] straight from the seeded workout. */
fun newSessionEngine(options: PlanOptions): SessionEngine = SessionEngine(SessionPlanner.plan(options))
