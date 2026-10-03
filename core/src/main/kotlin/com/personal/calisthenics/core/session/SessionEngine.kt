package com.personal.calisthenics.core.session

import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.plan.FlowBlock
import com.personal.calisthenics.core.plan.FlowItem
import com.personal.calisthenics.core.plan.RestPlanner
import com.personal.calisthenics.core.plan.SessionPlan
import com.personal.calisthenics.core.plan.SetBlock
import com.personal.calisthenics.core.plan.WorkItem
import com.personal.calisthenics.core.timer.CueType
import com.personal.calisthenics.core.timer.PhaseKind
import com.personal.calisthenics.core.timer.TimelineTimer
import com.personal.calisthenics.core.timer.TimerSnapshot
import com.personal.calisthenics.core.timer.Timers

enum class SessionMode {
    NOT_STARTED,

    /** Warm-up or decompression flow is running by itself. */
    FLOW,

    /** A set is next; waiting for the user to tap Start. */
    READY,

    /** Isometric (get-ready + hold) or tempo metronome is running. */
    ACTIVE,

    /** The set is over; waiting for reps / RIR (or hold seconds) to be logged. */
    AWAITING_LOG,

    /** Smart rest countdown after a logged set. */
    RESTING,
    FINISHED,
}

/** What the log sheet should be pre-filled with when a set ends. */
data class SetSuggestion(val reps: Int?, val holdSeconds: Int?)

data class SetResult(val item: WorkItem, val reps: Int?, val holdSeconds: Int?, val rir: Int?)

data class EngineSnapshot(
    val mode: SessionMode,
    val phase: Phase?,
    val timer: TimerSnapshot?,
    val paused: Boolean,
    /** Set being done (READY / ACTIVE / AWAITING_LOG) or about to be done (RESTING). */
    val item: WorkItem?,
    /** The set after [item]; during rest this equals [item]. */
    val nextItem: WorkItem?,
    /** Current drill while in [SessionMode.FLOW]. */
    val flowItem: FlowItem?,
    val setsLogged: Int,
    val setsTotal: Int,
    val suggestion: SetSuggestion,
    val restSeconds: Int,
)

/**
 * Pure, clock-injected state machine behind the Workout Player. It sequences the plan's blocks:
 * flow blocks run on their own, set blocks wait for Start, run the isometric / tempo timer, wait for the log,
 * then start the smart rest automatically. The UI layer only forwards taps and calls [poll] a few times a second.
 */
class SessionEngine(val plan: SessionPlan) {

    var mode: SessionMode = SessionMode.NOT_STARTED
        private set
    var paused: Boolean = false
        private set

    private var blockIndex = -1
    private var itemIndex = 0
    private var timer: TimelineTimer? = null
    private var restSeconds = 0
    private var suggestion = SetSuggestion(null, null)
    private val results = mutableListOf<SetResult>()
    private val workItems: List<WorkItem> = plan.workItems

    val loggedResults: List<SetResult> get() = results.toList()

    fun start(nowMs: Long) {
        if (mode != SessionMode.NOT_STARTED) return
        enterBlock(0, nowMs)
    }

    /** Advances timers and transitions; returns the cues to play (in order). */
    fun poll(nowMs: Long): List<CueType> {
        val t = timer ?: return emptyList()
        val cues = t.poll(nowMs).map { it.type }
        if (t.isFinished) onTimerFinished(nowMs)
        return cues
    }

    fun pause(nowMs: Long) {
        if (paused || mode == SessionMode.NOT_STARTED || mode == SessionMode.FINISHED) return
        timer?.pause(nowMs)
        paused = true
    }

    fun resume(nowMs: Long) {
        if (!paused) return
        timer?.resume(nowMs)
        paused = false
    }

    /** READY -> ACTIVE. Isometric sets get the 5 s get-ready buffer; tempo sets get the metronome. */
    fun startSet(nowMs: Long) {
        if (mode != SessionMode.READY) return
        val item = currentItem() ?: return
        timer = when (item.kind) {
            StepKind.ISOMETRIC -> {
                val max = item.holdMaxSec ?: 20
                Timers.isometric((item.holdMinSec ?: max).coerceIn(1, max), max)
            }
            StepKind.STRENGTH -> item.tempo?.let { Timers.tempo(it, plannedReps(item)) }
            StepKind.FLOW -> null
        }
        timer?.start(nowMs)
        paused = false
        mode = SessionMode.ACTIVE
    }

    /** ACTIVE -> AWAITING_LOG before the timer ran out ("I'm done" / failure / dropped out of the hold). */
    fun finishSetEarly(nowMs: Long) {
        if (mode != SessionMode.ACTIVE) return
        suggestion = computeSuggestion(nowMs, completed = false)
        timer = null
        paused = false
        mode = SessionMode.AWAITING_LOG
    }

    /** Records the set, then starts the rest (or moves straight on when nothing but stretching follows). */
    fun logSet(nowMs: Long, reps: Int?, holdSeconds: Int?, rir: Int?): SetResult? {
        if (mode != SessionMode.AWAITING_LOG) return null
        val item = currentItem() ?: return null
        val result = SetResult(item, reps, holdSeconds, rir)
        results += result

        val isLastInBlock = itemIndex == (blocks()[blockIndex] as SetBlock).items.lastIndex
        val nextBlock = blocks().getOrNull(blockIndex + 1)
        val noRestNeeded = isLastInBlock && nextBlock !is SetBlock
        if (noRestNeeded) {
            advance(nowMs)
        } else {
            val cap = if (plan.options.coldMode && item.supersetWith != null) RestPlanner.COLD_MODE_REST_SEC else null
            restSeconds = RestPlanner.restSeconds(item.restMinSec, item.restMaxSec, rir, cap)
            timer = Timers.rest(restSeconds).also { it.start(nowMs) }
            paused = false
            mode = SessionMode.RESTING
        }
        return result
    }

    fun skipRest(nowMs: Long) {
        if (mode == SessionMode.RESTING) advance(nowMs)
    }

    /** Adds [seconds] to the running rest. */
    fun extendRest(nowMs: Long, seconds: Int = 30) {
        if (mode != SessionMode.RESTING) return
        val remaining = timer?.snapshot(nowMs)?.phaseRemainingMs ?: 0L
        restSeconds = ((remaining + 999L) / 1000L).toInt() + seconds
        timer = Timers.rest(restSeconds).also { it.start(nowMs) }
        if (paused) timer?.pause(nowMs)
    }

    fun skipFlowForward(nowMs: Long) {
        if (mode == SessionMode.FLOW) timer?.skipToNextPhase(nowMs)
    }

    fun skipFlowBack(nowMs: Long) {
        if (mode == SessionMode.FLOW) timer?.skipToPreviousPhase(nowMs)
    }

    /** Ends the session at any time (finish early or after the last block). */
    fun finish() {
        timer = null
        paused = false
        mode = SessionMode.FINISHED
    }

    fun snapshot(nowMs: Long): EngineSnapshot {
        val block = blocks().getOrNull(blockIndex)
        val t = timer?.snapshot(nowMs)
        val item = currentItem()
        val flowItem = if (mode == SessionMode.FLOW && block is FlowBlock) {
            t?.phase?.itemIndex?.let { block.items.getOrNull(it) }
        } else {
            null
        }
        val nextItem = when (mode) {
            SessionMode.RESTING -> item
            SessionMode.READY, SessionMode.ACTIVE, SessionMode.AWAITING_LOG -> workItems.getOrNull(results.size + 1)
            else -> null
        }
        return EngineSnapshot(
            mode = mode, phase = block?.phase, timer = t, paused = paused, item = item, nextItem = nextItem,
            flowItem = flowItem, setsLogged = results.size, setsTotal = workItems.size, suggestion = suggestion,
            restSeconds = restSeconds,
        )
    }

    // ------------------------------------------------------------------ internals

    private fun blocks() = plan.blocks

    /** In RESTING the logged set is already counted, so the upcoming set is the one after it. */
    private fun currentItem(): WorkItem? = when (mode) {
        SessionMode.READY, SessionMode.ACTIVE, SessionMode.AWAITING_LOG -> workItems.getOrNull(results.size)
        SessionMode.RESTING -> workItems.getOrNull(results.size)
        else -> null
    }

    private fun plannedReps(item: WorkItem): Int = (item.repsMax ?: item.repsMin ?: 8).coerceAtLeast(1)

    private fun enterBlock(index: Int, nowMs: Long) {
        var i = index
        while (true) {
            val block = blocks().getOrNull(i)
            if (block == null) {
                blockIndex = i
                finish()
                return
            }
            when (block) {
                is FlowBlock -> if (block.items.isNotEmpty()) {
                    blockIndex = i
                    timer = Timers.flow(block.items).also { it.start(nowMs) }
                    paused = false
                    mode = SessionMode.FLOW
                    return
                }
                is SetBlock -> if (block.items.isNotEmpty()) {
                    blockIndex = i
                    itemIndex = 0
                    timer = null
                    mode = SessionMode.READY
                    return
                }
            }
            i++
        }
    }

    /** Moves to the next set of the block, or into the next block. */
    private fun advance(nowMs: Long) {
        val block = blocks()[blockIndex]
        if (block is SetBlock && itemIndex < block.items.lastIndex) {
            itemIndex++
            timer = null
            paused = false
            mode = SessionMode.READY
        } else {
            enterBlock(blockIndex + 1, nowMs)
        }
    }

    private fun onTimerFinished(nowMs: Long) {
        when (mode) {
            SessionMode.FLOW -> enterBlock(blockIndex + 1, nowMs)
            SessionMode.ACTIVE -> {
                suggestion = computeSuggestion(nowMs, completed = true)
                timer = null
                mode = SessionMode.AWAITING_LOG
            }
            SessionMode.RESTING -> advance(nowMs)
            else -> timer = null
        }
    }

    private fun computeSuggestion(nowMs: Long, completed: Boolean): SetSuggestion {
        val item = currentItem() ?: return SetSuggestion(null, null)
        return when (item.kind) {
            StepKind.ISOMETRIC -> {
                val max = item.holdMaxSec ?: 20
                if (completed) {
                    SetSuggestion(null, max)
                } else {
                    val snap = timer?.snapshot(nowMs)
                    val holdMs = if (snap?.phase?.kind == PhaseKind.HOLD) snap.phaseElapsedMs else 0L
                    SetSuggestion(null, (holdMs / 1000L).toInt())
                }
            }
            StepKind.STRENGTH -> {
                val planned = plannedReps(item)
                if (completed || timer == null) {
                    SetSuggestion(planned, null)
                } else {
                    val rep = timer?.snapshot(nowMs)?.phase?.rep
                    SetSuggestion(((rep ?: 1) - 1).coerceIn(0, planned), null)
                }
            }
            StepKind.FLOW -> SetSuggestion(null, null)
        }
    }
}
