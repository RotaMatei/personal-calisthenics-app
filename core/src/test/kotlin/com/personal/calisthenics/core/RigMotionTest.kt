package com.personal.calisthenics.core

import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.RigSolver
import com.personal.calisthenics.core.rig.Skeleton
import com.personal.calisthenics.core.rig.Vec3
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.abs

/** M15: elbows and knees bend smoothly and the right way, and multi-part clips show every part. */
class RigMotionTest {

    private fun skeletons(anim: RigAnimation, stepMs: Long = 10L): List<Skeleton> =
        (0 until anim.loopMs step stepMs).map { RigSolver.solve(anim.poseAt(it)) }

    private fun angleDeg(a: Vec3, b: Vec3): Float {
        val la = a.length()
        val lb = b.length()
        if (la < 1e-4f || lb < 1e-4f) return 0f
        return Math.toDegrees(acos((a.dot(b) / (la * lb)).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    /** Interior elbow angles (180 = straight) of both arms for every frame. */
    private fun elbowAngles(frames: List<Skeleton>): List<Float> = frames.flatMap {
        listOf(angleDeg(it.shoulderL - it.elbowL, it.wristL - it.elbowL), angleDeg(it.shoulderR - it.elbowR, it.wristR - it.elbowR))
    }

    private fun bendOffset(root: Vec3, mid: Vec3, end: Vec3) = mid - (root + end) * 0.5f

    @Test
    fun elbowsAndKneesNeverFlipTheirBendDirectionBetweenFrames() {
        for (id in RigLibrary.animationIds) {
            val frames = skeletons(RigLibrary.animation(id))
            for ((a, b) in frames.zipWithNext()) {
                val pairs = listOf(
                    bendOffset(a.shoulderL, a.elbowL, a.wristL) to bendOffset(b.shoulderL, b.elbowL, b.wristL),
                    bendOffset(a.shoulderR, a.elbowR, a.wristR) to bendOffset(b.shoulderR, b.elbowR, b.wristR),
                    bendOffset(a.hipL, a.kneeL, a.ankleL) to bendOffset(b.hipL, b.kneeL, b.ankleL),
                    bendOffset(a.hipR, a.kneeR, a.ankleR) to bendOffset(b.hipR, b.kneeR, b.ankleR),
                )
                for ((p, q) in pairs) {
                    if (p.length() < 3f || q.length() < 3f) continue // nearly straight: direction is meaningless
                    assertTrue("$id: a joint flips its bend direction within 10 ms", angleDeg(p, q) <= 20f)
                }
            }
        }
    }

    @Test
    fun exercisesThatBendTheElbowReallyBendIt() {
        val bending = listOf(
            "strict_pullups", "australian_pullups", "parallel_bar_dips", "straight_bar_dips",
            "pike_pushups", "pseudo_planche_pushups",
        )
        for (id in bending) {
            val angles = elbowAngles(skeletons(RigLibrary.animation(id)))
            val min = angles.min()
            val max = angles.max()
            assertTrue("$id: elbow only bends to ${min.toInt()} degrees", min <= 100f)
            assertTrue("$id: elbow range is only ${(max - min).toInt()} degrees", max - min >= 60f)
        }
    }

    @Test
    fun straightArmExercisesKeepTheElbowsStraight() {
        for (id in listOf("passive_dead_hang", "scapular_pullups", "scapular_pushups", "scapular_dips", "planche_lean")) {
            val angles = elbowAngles(skeletons(RigLibrary.animation(id)))
            assertTrue("$id: elbows should stay nearly straight (min ${angles.min().toInt()})", angles.min() >= 150f)
        }
    }

    @Test
    fun pullUpElbowsStayBelowTheHandsAndNeverSwingBehindTheHead() {
        for (frame in skeletons(RigLibrary.animation("strict_pullups"))) {
            for ((elbow, wrist) in listOf(frame.elbowL to frame.wristL, frame.elbowR to frame.wristR)) {
                assertTrue("elbow rises above the bar hand", elbow.y <= wrist.y + 1f)
            }
            // The elbow must not go behind the line between shoulder and wrist by more than a forearm's length.
            assertTrue(abs(frame.elbowL.z - frame.shoulderL.z) < 45f)
        }
    }

    @Test
    fun jointCirclesPlaysEveryJointInTurnAndNamesIt() {
        val anim = RigLibrary.animation("joint_circles")
        val expected = listOf("Neck (half circles)", "Shoulders", "Elbows", "Hips", "Ankles")
        assertEquals(expected, anim.captions)
        val shown = (0 until anim.loopMs step 100L).mapNotNull { anim.captionAt(it) }.distinct()
        assertEquals("every joint is named on screen, in order", expected, shown)
        // The seeded drill splits its time over the same five names.
        val seeded = SeedData.step("w0_circles").flowParts.single().subLabels
        assertEquals(expected, seeded)

        // Each part's final keyframe is the hand-over to the next part, so only look at the circling itself.
        fun window(i: Int) = (i * 6000L until (i + 1) * 6000L - 400L step 20L).map { RigSolver.solve(anim.poseAt(it)) to anim.poseAt(it) }
        // Neck: the head rolls to both sides and nods forward.
        val neck = window(0).map { it.second }
        assertTrue(neck.maxOf { it.headRoll } >= 25f && neck.minOf { it.headRoll } <= -25f)
        assertTrue(neck.maxOf { it.headTilt } >= 25f)
        // Shoulders: straight arms sweep from below the hips to above the head.
        val shoulders = window(1).map { it.first }
        assertTrue(shoulders.minOf { it.wristR.y } < 100f && shoulders.maxOf { it.wristR.y } > 190f)
        assertTrue(shoulders.maxOf { it.wristR.z } > 40f && shoulders.minOf { it.wristR.z } < -40f)
        // Elbows: the elbow goes round while the hand stays near the shoulder.
        val elbows = window(2).map { it.first }
        assertTrue(elbows.maxOf { it.elbowR.y } - elbows.minOf { it.elbowR.y } > 30f)
        assertTrue(elbows.maxOf { it.elbowR.z } - elbows.minOf { it.elbowR.z } > 30f)
        assertTrue(elbows.all { (it.wristR - it.shoulderR).length() < 20f })
        // Hips: the pelvis moves sideways and back and forth while the shoulders stay put.
        val hips = window(3).map { it.first }
        assertTrue(hips.maxOf { it.hip.x } - hips.minOf { it.hip.x } > 12f)
        assertTrue(hips.maxOf { it.hip.z } - hips.minOf { it.hip.z } > 12f)
        assertTrue(hips.maxOf { it.shoulder.x } - hips.minOf { it.shoulder.x } < 6f)
        // Ankles: each foot lifts off the floor and the foot tilts through a wide range.
        val ankles = window(4)
        assertTrue(ankles.maxOf { it.first.ankleR.y } > 14f && ankles.maxOf { it.first.ankleL.y } > 14f)
        assertTrue(ankles.maxOf { it.second.footR.pitch } - ankles.minOf { it.second.footR.pitch } > 50f)
        assertTrue(ankles.maxOf { it.second.footL.pitch } - ankles.minOf { it.second.footL.pitch } > 50f)
    }

    @Test
    fun captionsOnlyAppearOnClipsThatNameTheirParts() {
        for (id in RigLibrary.animationIds) {
            val anim = RigLibrary.animation(id)
            if (id != "joint_circles") assertEquals("$id", null, anim.captionAt(1234L))
        }
    }
}
