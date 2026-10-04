package com.personal.calisthenics.core.rig

import com.personal.calisthenics.core.model.Highlight

/**
 * How the camera moves around the figure while a clip loops: it swings [sweepDeg] either side of [centerYaw] once
 * every [periodMs]. A manual drag adds a yaw offset on top; [sweepDeg] 0 gives a fixed camera.
 */
data class OrbitSpec(
    val centerYaw: Float = -38f,
    val sweepDeg: Float = 32f,
    val periodMs: Long = 14_000L,
    val pitch: Float = 12f,
    val perspective: Float = 0.3f,
)

/** Everything a canvas needs to draw one frame of a clip. */
class ClipFrame(
    /** Sorted back to front; paint in order. */
    val prims: List<Prim>,
    /** 0 at Position A, 1 at Position B. */
    val progress: Float,
    val camera: Camera,
    /** Name of the movement part being played (e.g. "Shoulders" in the joint-circles clip); null for plain clips. */
    val caption: String? = null,
)

/**
 * A looping 3D exercise clip: the rig animation, the exercise's stress highlights (coloured by load and rep phase)
 * and an orbiting camera. Pure Kotlin, so the Android canvas only has to paint [ClipFrame.prims].
 */
class ClipPlayer(
    val animation: RigAnimation,
    val highlights: List<Highlight>,
    val orbit: OrbitSpec = OrbitSpec(),
    val options: RenderOptions = RenderOptions(),
) {
    /** Camera at [timeMs] with an extra manual [yawOffset] (degrees) from dragging. */
    fun cameraAt(timeMs: Long, yawOffset: Float = 0f): Camera {
        val phase = if (orbit.periodMs <= 0L) 0.0 else (timeMs % orbit.periodMs).toDouble() / orbit.periodMs
        val swing = orbit.sweepDeg * kotlin.math.sin(2.0 * Math.PI * phase).toFloat()
        return Camera(yaw = orbit.centerYaw + swing + yawOffset, pitch = orbit.pitch, perspective = orbit.perspective)
    }

    fun frame(timeMs: Long, yawOffset: Float = 0f, camera: Camera = cameraAt(timeMs, yawOffset)): ClipFrame {
        val progress = animation.progressAt(timeMs)
        val draws = highlights.toDraws(progress)
        val prims = RigRenderer.render(animation.scene, animation.poseAt(timeMs), camera, draws, options)
        return ClipFrame(prims, progress, camera, animation.captionAt(timeMs))
    }

    /**
     * One fixed view rectangle (cm) that holds the whole figure for every camera angle the orbit can reach, so the
     * picture never zooms or jumps while the camera swings.
     */
    val bounds: Bounds by lazy { union(orbitYaws().map { cam(it) }) }

    /** Bounds that stay valid while the user drags the camera to any angle around the figure. */
    val boundsAnyYaw: Bounds by lazy { union((orbitYaws() + (0 until 12).map { it * 30f }).map { cam(it) }) }

    private fun orbitYaws(): List<Float> =
        if (orbit.sweepDeg == 0f) listOf(orbit.centerYaw)
        else listOf(-1f, -0.5f, 0f, 0.5f, 1f).map { orbit.centerYaw + it * orbit.sweepDeg }

    private fun cam(yaw: Float) = Camera(yaw = yaw, pitch = orbit.pitch, perspective = orbit.perspective)

    private fun union(cams: List<Camera>): Bounds {
        val all = cams.map { RigFraming.bounds(animation, it) }
        return Bounds(all.minOf { it.minX }, all.minOf { it.minY }, all.maxOf { it.maxX }, all.maxOf { it.maxY })
    }
}

/** Same motion, but one full loop lasts [targetLoopMs] (used to play a rep at the exercise's real tempo). */
fun RigAnimation.scaledToLoop(targetLoopMs: Long): RigAnimation {
    require(targetLoopMs > 0) { "Loop length must be positive" }
    val k = targetLoopMs.toDouble() / loopMs
    return RigAnimation(
        scene,
        keyframes.map {
            Keyframe(it.pose, (it.holdMs * k).toLong().coerceAtLeast(1L), (it.moveMs * k).toLong().coerceAtLeast(1L), it.label, it.flow)
        },
    )
}
