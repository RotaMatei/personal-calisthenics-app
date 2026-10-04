package com.personal.calisthenics.core.rig

import com.personal.calisthenics.core.model.Tempo

/** Maps workout-timer time to clip time, so the 3D clip on screen matches the timer and its beeps. */
object ClipSync {

    /**
     * Clip time for a warm-up or stretching drill that has run for [drillElapsedMs] of its [drillMs].
     *
     * A clip made of several captioned parts (the joint circles: neck, shoulders, elbows, hips, ankles) is stretched so
     * exactly one loop spans the drill. The drill's timer sub-labels split it into equal slices, so each part of the
     * clip then plays during its own sub-label. Every other clip runs at its own speed.
     */
    fun drillTimeMs(animation: RigAnimation, drillElapsedMs: Long, drillMs: Long): Long {
        val elapsed = drillElapsedMs.coerceAtLeast(0L)
        if (animation.captions.size < 2 || drillMs <= 0L) return elapsed
        return (elapsed.toDouble() * animation.loopMs / drillMs).toLong()
    }

    /**
     * Clip time for a tempo set [setElapsedMs] after Start. The clip made by [animationAtTempo] begins with the top
     * hold, so the lead-in (3 s of "get set") rests at the top and the first lowering starts together with the first
     * "lower" beep; from then on the clip loops once per rep in step with the beeps.
     */
    fun tempoTimeMs(setElapsedMs: Long, leadInMs: Long, tempo: Tempo): Long =
        tempo.topPauseSec * 1000L + (setElapsedMs - leadInMs).coerceAtLeast(0L)

    /**
     * The exercise's motion retimed to a rep tempo, so on screen it lowers, pauses, drives and squeezes exactly as the
     * beeps do. Works for the two-pose rep clips (position A, position B): the pose with the higher body is the top
     * of the rep (lock-out, chin over the bar, standing), the other the bottom. Clips of any other shape are only
     * stretched to the rep length.
     */
    fun animationAtTempo(base: RigAnimation, tempo: Tempo): RigAnimation {
        val repMs = maxOf(tempo.repSeconds, 3) * 1000L
        if (base.keyframes.size != 2 || base.captions.isNotEmpty()) return base.scaledToLoop(repMs)
        val heights = base.keyframes.map { centreHeight(RigSolver.solve(it.pose)) }
        val top = base.keyframes[if (heights[0] >= heights[1]) 0 else 1].pose
        val bottom = base.keyframes[if (heights[0] >= heights[1]) 1 else 0].pose
        return RigAnimation(
            base.scene,
            listOf(
                Keyframe(top, holdMs = tempo.topPauseSec * 1000L, moveMs = maxOf(tempo.eccentricSec, 1) * 1000L),
                Keyframe(bottom, holdMs = tempo.bottomPauseSec * 1000L, moveMs = maxOf(tempo.concentricSec, 1) * 1000L),
            ),
        )
    }

    /** Average height of the main joints: the body is highest at the top of a rep. */
    private fun centreHeight(sk: Skeleton): Float =
        listOf(sk.shoulder, sk.hip, sk.headCenter, sk.kneeL, sk.kneeR, sk.ankleL, sk.ankleR, sk.wristL, sk.wristR)
            .map { it.y }.average().toFloat()
}
