package com.personal.calisthenics.core.rig

import kotlin.math.PI
import kotlin.math.cos

/**
 * One resting keyframe: hold [holdMs] in [pose], then move to the next keyframe over [moveMs]. A [label] names the
 * part of a multi-part clip that starts at this keyframe (for example the joint being circled); it is shown as a
 * caption until the next label. With [flow] the move is linear instead of easing in and out, so a chain of
 * keyframes along a circle plays as one continuous sweep without stopping at every keyframe.
 */
data class Keyframe(
    val pose: Pose,
    val holdMs: Long = 500L,
    val moveMs: Long = 1200L,
    val label: String? = null,
    val flow: Boolean = false,
)

/**
 * A looping exercise demonstration: a scene plus an ordered, cyclic list of keyframes. A typical rep is
 * Position A (start) then Position B (end range), and the loop returns from B to A.
 */
class RigAnimation(
    val scene: RigScene,
    val keyframes: List<Keyframe>,
    /**
     * Poses that [progressAt] measures from (Position A) and to (Position B) when they are not simply the first keyframe
     * and its farthest pose: a clip retimed to a rep tempo starts at the top of the rep, which for pull-ups is Position B.
     */
    private val progressFrom: Pose? = null,
    private val progressTo: Pose? = null,
) {
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
                val u = t.toFloat() / k.moveMs
                return k.pose.lerpTo(next.pose, if (k.flow) u else ease(u))
            }
            t -= k.moveMs
        }
        return keyframes.first().pose
    }

    /** The distinct captions of this clip in playing order (empty when no keyframe has a label). */
    val captions: List<String> = keyframes.mapNotNull { it.label }.distinct()

    /**
     * Caption to show at [timeMs]: the label of the part being played, switching halfway through the move into the
     * next labelled keyframe. Null when the clip has no labels.
     */
    fun captionAt(timeMs: Long): String? {
        if (captions.isEmpty()) return null
        var t = ((timeMs % loopMs) + loopMs) % loopMs
        var active = 0
        for (i in keyframes.indices) {
            val k = keyframes[i]
            if (t < k.holdMs) { active = i; break }
            t -= k.holdMs
            if (t < k.moveMs) {
                active = if (t * 2 >= k.moveMs) (i + 1) % keyframes.size else i
                break
            }
            t -= k.moveMs
        }
        // The label in force is the most recent one at or before the active keyframe (wrapping around the loop).
        var i = active
        repeat(keyframes.size) {
            keyframes[i].label?.let { return it }
            i = (i - 1 + keyframes.size) % keyframes.size
        }
        return null
    }

    /** First keyframe, handy for static previews. */
    fun startPose(): Pose = keyframes.first().pose

    /** The keyframe pose that differs most from the first one (the "end of range" for a rep). */
    fun endPose(): Pose = keyframes.drop(1).maxByOrNull { distance(keyframes.first().pose, it.pose) }!!.pose

    private val fromPose: Pose get() = progressFrom ?: keyframes.first().pose
    private val spanToEnd: Float by lazy { distance(fromPose, progressTo ?: endPose()) }

    /**
     * How far the clip is from Position A (0) to the end-of-range Position B (1) at [timeMs]; it drives how strongly
     * each anatomy highlight is drawn (see [com.personal.calisthenics.core.model.Highlight.strengthAt]).
     */
    fun progressAt(timeMs: Long): Float {
        if (spanToEnd < 1e-3f) return 0f
        return (distance(fromPose, poseAt(timeMs)) / spanToEnd).coerceIn(0f, 1f)
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
    fun bounds(animation: RigAnimation, camera: Camera, margin: Float = 10f): Bounds =
        boundsAt(animation, (0..12).map { animation.loopMs * it / 12 }, camera, margin)

    /** Like [bounds] but only for the poses at [times] (a thumbnail shows one pose, so it needs no sweep of the loop). */
    fun boundsAt(animation: RigAnimation, times: List<Long>, camera: Camera, margin: Float = 10f): Bounds {
        // Measuring renders the figure many times (hundreds of ms on a phone), and the same clip is framed again every
        // time a screen shows it, so remember the answer. The key holds references to the clip's own objects.
        val key = listOf(animation.scene, animation.keyframes, times, camera, margin)
        return cache.getOrPut(key) { measure(animation, times, camera, margin) }
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<List<Any>, Bounds>()

    private fun measure(animation: RigAnimation, times: List<Long>, camera: Camera, margin: Float): Bounds {
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
        for (time in times) {
            val pose = animation.poseAt(time)
            for (prim in RigRenderer.render(animation.scene, pose, camera, emptyList(), options)) {
                if (prim.material in RigRenderer.groundMaterials) continue
                when (prim) {
                    is CapsulePrim -> { include(prim.a.x, prim.a.y, prim.ra); include(prim.b.x, prim.b.y, prim.rb) }
                    is DiscPrim -> include(prim.c.x, prim.c.y, prim.r)
                    is EllipsePrim -> { val r = maxOf(prim.rx, prim.ry); include(prim.c.x, prim.c.y, r) }
                    is PolyPrim -> prim.points.forEach { include(it.x, it.y, 0f) }
                    is LinePrim -> { include(prim.a.x, prim.a.y, 0f); include(prim.b.x, prim.b.y, 0f) }
                    is WashPrim -> Unit // clipped to body parts that are already counted
                }
            }
        }
        return Bounds(minX - margin, minY - margin, maxX + margin, maxY + margin)
    }

    fun bounds(animation: RigAnimation, view: ViewKind, margin: Float = 10f): Bounds = bounds(animation, view.camera, margin)
}
