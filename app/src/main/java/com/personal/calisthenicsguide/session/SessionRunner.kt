package com.personal.calisthenicsguide.session

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.plan.SessionPlan
import com.personal.calisthenics.core.plan.SessionPlanner
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenics.core.session.EngineSnapshot
import com.personal.calisthenics.core.session.SessionEngine
import com.personal.calisthenics.core.session.SessionMode
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.data.SettingKeys
import com.personal.calisthenicsguide.feedback.CueFeedback
import com.personal.calisthenicsguide.service.WorkoutService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the UI and the foreground-service notification observe. */
data class RunnerState(
    val active: Boolean = false,
    val sessionId: Long = 0L,
    val plan: SessionPlan? = null,
    val snapshot: EngineSnapshot? = null,
)

/**
 * Owns the running workout: the pure [SessionEngine], the ~10 Hz tick loop, cue feedback and persistence.
 * Lives in the Application so it survives configuration changes, and a [WorkoutService] keeps the process
 * (and a wake lock) alive while a session is active.
 */
class SessionRunner(
    private val context: Context,
    private val repository: Repository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val feedback = CueFeedback(context)

    private val _state = MutableStateFlow(RunnerState())
    val state: StateFlow<RunnerState> = _state.asStateFlow()

    private var engine: SessionEngine? = null
    private var sessionId = 0L
    private var tickJob: Job? = null
    private var soundOn = true
    private var vibrationOn = true

    init {
        scope.launch {
            repository.settings.collect { map ->
                soundOn = map[SettingKeys.SOUND_ON] != "false"
                vibrationOn = map[SettingKeys.VIBRATION_ON] != "false"
            }
        }
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    /** Starts a session for [options]; the foreground service starts as soon as the session row exists. */
    fun start(options: PlanOptions) {
        if (_state.value.active) return
        scope.launch {
            val plan = SessionPlanner.plan(options)
            sessionId = repository.startSession(options.day, options.deload, options.coldMode, System.currentTimeMillis())
            val e = SessionEngine(plan)
            engine = e
            e.start(now())
            ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            publish(active = true, plan = plan)
            tickJob?.cancel()
            tickJob = scope.launch {
                while (isActive) {
                    tick()
                    delay(TICK_MS)
                }
            }
        }
    }

    private fun tick() {
        val e = engine ?: return
        for (cue in e.poll(now())) feedback.play(cue, soundOn, vibrationOn)
        publish(active = true)
    }

    private fun publish(active: Boolean, plan: SessionPlan? = _state.value.plan) {
        val e = engine
        _state.value = RunnerState(
            active = active,
            sessionId = sessionId,
            plan = plan,
            snapshot = e?.snapshot(now()),
        )
    }

    // ---------------------------------------------------------------- user actions

    fun startSet() = act { it.startSet(now()) }

    fun finishSetEarly() = act { it.finishSetEarly(now()) }

    fun skipRest() = act { it.skipRest(now()) }

    fun extendRest() = act { it.extendRest(now()) }

    fun skipFlowForward() = act { it.skipFlowForward(now()) }

    fun skipFlowBack() = act { it.skipFlowBack(now()) }

    fun togglePause() = act { if (it.paused) it.resume(now()) else it.pause(now()) }

    private fun act(block: (SessionEngine) -> Unit) {
        val e = engine ?: return
        block(e)
        publish(active = true)
    }

    /** Logs the set that just ended and starts the smart rest. */
    fun logSet(reps: Int?, holdSeconds: Int?, rir: Int?) {
        val e = engine ?: return
        val result = e.logSet(now(), reps, holdSeconds, rir) ?: return
        publish(active = true)
        val id = sessionId
        scope.launch {
            val item = result.item
            val exercise = SeedData.exercise(item.exerciseId)
            repository.logSet(
                sessionId = id,
                nowMs = System.currentTimeMillis(),
                exerciseId = item.exerciseId,
                category = exercise.category,
                stepId = item.stepId,
                setNumber = item.setNumber,
                side = item.side?.name,
                reps = reps,
                holdSeconds = holdSeconds,
                rir = rir,
                levelIndex = repository.progressionOf(item.exerciseId).levelIndex,
            )
        }
    }

    /** True once every set is done (or the user ended the session) and only the joint-log screen remains. */
    fun isFinished(): Boolean = engine?.mode == SessionMode.FINISHED

    /** Stops the session. [rating] is the post-workout joint log; a session without any logged set is discarded. */
    fun finish(rating: JointRating?) {
        val e = engine ?: return
        val id = sessionId
        val hadSets = e.loggedResults.isNotEmpty()
        e.finish()
        tickJob?.cancel()
        tickJob = null
        engine = null
        publish(active = false, plan = null)
        scope.launch {
            if (hadSets) repository.finishSession(id, rating, System.currentTimeMillis()) else repository.discardIfEmpty(id)
        }
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
