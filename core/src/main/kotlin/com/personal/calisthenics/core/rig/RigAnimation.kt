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

    private val spanToEnd: Float by lazy { distance(keyframes.first().pose, endPose()) }

    /**
     * How far the clip is from Position A (0) to the end-of-range Position B (1) at [timeMs]; it drives how strongly
     * each anatomy highlight is drawn (see [com.personal.calisthenics.core.model.Highlight.strengthAt]).
     */
    fun progressAt(timeMs: Long): Float {
        if (spanToEnd < 1e-3f) return 0f
        return (distance(keyframes.first().pose, poseAt(timeMs)) / spanToEnd).coerceIn(0f, 1f)
    }

    private fun distance(a: Pose, b: Pose): Float {
        fun d(x: Limb, y: Limb) = (x.target - y.target).length()
        return d(a.handL, b.handL) + d(a.handR, b.handR) + d(a.footL, b.footL) + d(a.footR, b.footR) +
            (a.anchor - b.anchor).length() + kotlin.math.abs(a.lean - b.lean) * 0.5f +
            kotlin.math.abs(a.spineFlex - b.spineFlex) * 0.5f + kotlin.math.abs(a.shrug - b.shrug)
    }

    private fun ease(t: Float): Float = ((1.0 - cos(PI * t.coerceIn(0f, 1f))) / 2.0).toFloat()
}

/** Rectangle in view centimetres used to frame an animation. */
data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

object RigFraming {
    /**
     * Union bounds of everything the animation draws from [camera] (ground excluded), expanded by a margin, so the
     * figure never leaves the frame while it moves.
     */
    fun bounds(animation: RigAnimation, camera: Camera, margin: Float = 10f, samples: Int = 12): Bounds {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        fun include(x: Float, y: Float, r: Float) {
            minX = minOf(minX, x - r)
            minY = minOf(minY, y - r)
            maxX = maxOf(maxX, x + r)
            maxY = maxOf(maxY, y + r)
        }
        val options = RenderOptions(xray = false)
        for (frame in 0..samples) {
            val pose = animation.poseAt(animation.loopMs * frame / samples)
            for (prim in RigRenderer.render(animation.scene, pose, camera, emptyList(), options)) {
                if (prim.material in RigRenderer.groundMaterials) continue
                when (prim) {
                    is CapsulePrim -> { include(prim.a.x, prim.a.y, prim.ra); include(prim.b.x, prim.b.y, prim.rb) }
                    is DiscPrim -> include(prim.c.x, prim.c.y, prim.r)
                    is EllipsePrim -> { val r = maxOf(prim.rx, prim.ry); include(prim.c.x, prim.c.y, r) }
                    is PolyPrim -> prim.points.forEach { include(it.x, it.y, 0f) }
                    is LinePrim -> { include(prim.a.x, prim.a.y, 0f); include(prim.b.x, prim.b.y, 0f) }
                }
            }
        }
        return Bounds(minX - margin, minY - margin, maxX + margin, maxY + margin)
    }

    fun bounds(animation: RigAnimation, view: ViewKind, margin: Float = 10f): Bounds = bounds(animation, view.camera, margin)
}
