package com.personal.calisthenics.core.mesh

import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.Box
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.ClipPlayer
import com.personal.calisthenics.core.rig.Cylinder
import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigScene
import com.personal.calisthenics.core.rig.RigSolver
import com.personal.calisthenics.core.rig.Skeleton
import com.personal.calisthenics.core.rig.Vec3
import kotlin.math.max
import kotlin.math.min

/**
 * Frames a clip for the mesh picture without rendering anything: the solved skeleton's joints, each with the radius of
 * the body around it, plus the props, are projected through the camera. That makes it cheap enough for long clips (the
 * joint circles have dozens of keyframes) and for every camera angle of the orbit, so a screen never stalls on it.
 * The radii are generous enough to hold the mesh; `HumanMeshTest` checks that for every exercise.
 */
object MeshFraming {

    private class Ball(val p: Vec3, val r: Float)

    /** Room around everything, in cm. */
    private const val MARGIN_CM = 3f

    /** Times worth measuring: every keyframe, the start and the middle of every move (limbs swing between keyframes). */
    fun sampleTimes(animation: RigAnimation): List<Long> {
        val times = sortedSetOf<Long>()
        var at = 0L
        for (k in animation.keyframes) {
            times += at
            times += at + k.holdMs
            times += at + k.holdMs + k.moveMs / 2
            at += k.holdMs + k.moveMs
        }
        for (i in 0..12) times += animation.loopMs * i / 12
        return times.map { it % animation.loopMs }.distinct()
    }

    /** View rectangle (cm) that holds the figure and props of [animation] at [times] from every camera in [cameras]. */
    fun bounds(animation: RigAnimation, cameras: List<Camera>, times: List<Long> = sampleTimes(animation)): Bounds {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        fun include(cam: Camera, ball: Ball) {
            val q = cam.project(ball.p)
            val r = ball.r * q.scale
            minX = min(minX, q.x - r); maxX = max(maxX, q.x + r)
            minY = min(minY, q.y - r); maxY = max(maxY, q.y + r)
        }
        val props = propBalls(animation.scene)
        for (cam in cameras) for (b in props) include(cam, b)
        for (t in times) {
            val balls = bodyBalls(RigSolver.solve(animation.poseAt(t)))
            for (cam in cameras) for (b in balls) include(cam, b)
        }
        return Bounds(minX - MARGIN_CM, minY - MARGIN_CM, maxX + MARGIN_CM, maxY + MARGIN_CM)
    }

    /** Framing of [player]'s clip for the orbiting camera; [anyYaw] also holds every angle the user can drag it to. */
    fun clip(player: ClipPlayer, anyYaw: Boolean): Bounds = bounds(player.animation, player.framingCameras(anyYaw))

    /** Framing of one pose (a thumbnail) from [camera]. */
    fun pose(animation: RigAnimation, timeMs: Long, camera: Camera): Bounds = bounds(animation, listOf(camera), listOf(timeMs))

    private fun propBalls(scene: RigScene): List<Ball> {
        val out = ArrayList<Ball>()
        for (item in scene.equipment) {
            when (item) {
                is Cylinder -> { out += Ball(item.a, item.r); out += Ball(item.b, item.r) }
                is Box -> for (x in listOf(item.min.x, item.max.x)) for (y in listOf(item.min.y, item.max.y)) for (z in listOf(item.min.z, item.max.z)) {
                    out += Ball(Vec3(x, y, z), 0f)
                }
            }
        }
        return out
    }

    private fun bodyBalls(sk: Skeleton): List<Ball> = listOf(
        Ball(sk.headCenter, 14f),
        Ball(sk.shoulderL, 11f), Ball(sk.shoulderR, 11f),
        Ball(sk.shoulder, 14f), Ball(sk.mid, 17f), Ball(sk.hip, 17f),
        Ball(sk.elbowL, 8f), Ball(sk.elbowR, 8f),
        Ball(sk.wristL, 8f), Ball(sk.wristR, 8f),
        Ball(sk.handTipL, 8f), Ball(sk.handTipR, 8f),
        Ball(sk.hipL, 14f), Ball(sk.hipR, 14f),
        Ball(sk.kneeL, 10f), Ball(sk.kneeR, 10f),
        Ball(sk.ankleL, 9f), Ball(sk.ankleR, 9f),
        Ball(sk.heelL, 8f), Ball(sk.heelR, 8f),
        Ball(sk.toeL, 8f), Ball(sk.toeR, 8f),
    )
}
