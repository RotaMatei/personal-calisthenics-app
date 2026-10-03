package com.personal.calisthenics.core.rig

import kotlin.math.PI
import kotlin.math.cos

/** One resting keyframe: hold [holdMs] in [pose], then move to the next keyframe over [moveMs]. */
data class Keyframe(val pose: Pose, val holdMs: Long = 500L, val moveMs: Long = 1200L)

/**
 * A looping exercise demonstration: a scene plus an ordered, cyclic list of keyframes. A typical rep is
 * Position A (start) then Position B (end range), and the loop returns from B to A.
 */
class RigAnimation(val scene: RigScene, val keyframes: List<Keyframe>) {
    init {
        require(keyframes.size >= 2) { "An animation needs at least two keyframes" }
        require(keyframes.all { it.pose.anchorAt == keyframes[0].pose.anchorAt }) { "All keyframes must share an anchor" }
    }

    val loopMs: Long = keyframes.sumOf { it.holdMs + it.moveMs }

    /** Smooth pose at time [timeMs] (any non-negative value; the animation loops). */
    fun poseAt(timeMs: Long): Pose {
        var t = ((timeMs % loopMs) + loopMs) % loopMs
        for (i in keyframes.indices) {
            val k = keyframes[i]
            if (t < k.holdMs) return k.pose
            t -= k.holdMs
            if (t < k.moveMs) {
                val next = keyframes[(i + 1) % keyframes.size]
                return k.pose.lerpTo(next.pose, ease(t.toFloat() / k.moveMs))
            }
            t -= k.moveMs
        }
        return keyframes.first().pose
    }

    /** First keyframe, handy for static previews. */
    fun startPose(): Pose = keyframes.first().pose

    /** The keyframe pose that differs most from the first one (the "end of range" for a rep). */
    fun endPose(): Pose = keyframes.drop(1).maxByOrNull { distance(keyframes.first().pose, it.pose) }!!.pose

    private fun distance(a: Pose, b: Pose): Float {
        fun d(x: Limb, y: Limb) = (x.target - y.target).length()
        return d(a.handL, b.handL) + d(a.handR, b.handR) + d(a.footL, b.footL) + d(a.footR, b.footR) +
            (a.anchor - b.anchor).length() + kotlin.math.abs(a.lean - b.lean) * 0.5f +
            kotlin.math.abs(a.spineFlex - b.spineFlex) * 0.5f + kotlin.math.abs(a.shrug - b.shrug)
    }

    private fun ease(t: Float): Float = ((1.0 - cos(PI * t.coerceIn(0f, 1f))) / 2.0).toFloat()
}

/** World-space rectangle used to frame an animation. */
data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

object RigFraming {
    /**
     * Union bounds of everything the animation draws in [view], expanded by a margin, so the figure never
     * leaves the frame while it moves. The floor is always included.
     */
    fun bounds(animation: RigAnimation, view: ViewKind, margin: Float = 14f): Bounds {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        fun include(p: Vec2, r: Float) {
            minX = minOf(minX, p.x - r)
            minY = minOf(minY, p.y - r)
            maxX = maxOf(maxX, p.x + r)
            maxY = maxOf(maxY, p.y + r)
        }
        for (frame in 0..12) {
            val pose = animation.poseAt(animation.loopMs * frame / 12)
            val sk = RigSolver.solve(pose)
            for (prim in RigRenderer.render(animation.scene, sk, view)) {
                when (prim) {
                    is CapsulePrim -> { include(prim.a, prim.ra); include(prim.b, prim.rb) }
                    is DiscPrim -> include(prim.c, prim.r)
                    is RectPrim -> { include(prim.min, 0f); include(prim.max, 0f) }
                    is FloorPrim -> if (animation.scene.floor) include(Vec2(0f, prim.y), 0f)
                }
            }
        }
        return Bounds(minX - margin, minY - margin, maxX + margin, maxY + margin)
    }
}
