package com.personal.calisthenics.core

import com.personal.calisthenics.core.rig.Body
import com.personal.calisthenics.core.rig.DetailArt
import com.personal.calisthenics.core.rig.Pose
import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigFraming
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.RigRenderer
import com.personal.calisthenics.core.rig.RigSolver
import com.personal.calisthenics.core.rig.RigValidation
import com.personal.calisthenics.core.rig.ViewKind
import com.personal.calisthenics.core.rig.toDraws
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RigTest {

    private fun allPoses(): List<Pair<String, Pose>> = buildList {
        for (e in SeedData.exercises) {
            val anim = RigLibrary.animation(e.id)
            anim.keyframes.forEachIndexed { i, k -> add("${e.id}#kf$i" to k.pose) }
            for (dd in e.doDonts) for (key in listOf(dd.wrongPoseKey, dd.rightPoseKey)) {
                RigLibrary.still(key)?.let { add(key to it.pose) }
            }
        }
    }

    @Test
    fun everyExerciseHasAnAnimation() {
        for (e in SeedData.exercises) assertNotNull("animation for ${e.id}", RigLibrary.animationOrNull(e.id))
        assertEquals(SeedData.exercises.map { it.id }.toSet(), RigLibrary.animationIds)
    }

    @Test
    fun everyDoDontKeyHasArt() {
        for (e in SeedData.exercises) for (dd in e.doDonts) {
            assertTrue("${e.id}: ${dd.wrongPoseKey}", RigLibrary.hasArt(dd.wrongPoseKey))
            assertTrue("${e.id}: ${dd.rightPoseKey}", RigLibrary.hasArt(dd.rightPoseKey))
        }
    }

    @Test
    fun noOrphanStills() {
        val keys = SeedData.exercises.flatMap { e -> e.doDonts.flatMap { listOf(it.wrongPoseKey, it.rightPoseKey) } }.toSet()
        for (k in RigLibrary.stillKeys) assertTrue("unused still $k", k in keys)
        for (k in DetailArt.keys) assertTrue("unused detail $k", k in keys)
    }

    @Test
    fun detailArtHasContent() {
        for (k in DetailArt.keys) {
            val scene = DetailArt.scene(k)
            assertNotNull(k, scene)
            assertTrue(k, scene!!.prims.size >= 5)
            assertTrue(k, scene.bounds.width > 0f && scene.bounds.height > 0f)
        }
    }

    @Test
    fun contactLimbsReachTheirTargets() {
        for ((name, pose) in allPoses()) {
            val errors = RigValidation.contactErrors(pose)
            assertTrue("$name contact errors: $errors", errors.isEmpty())
        }
    }

    @Test
    fun nothingSinksThroughTheFloor() {
        for ((name, pose) in allPoses()) {
            val pen = RigValidation.floorPenetration(pose)
            assertTrue("$name penetrates the floor by $pen cm", pen < 2.5f)
        }
    }

    @Test
    fun limbLengthsAreRespected() {
        for ((name, pose) in allPoses()) {
            val sk = RigSolver.solve(pose)
            fun close(label: String, actual: Float, expected: Float) =
                assertTrue("$name $label expected $expected got $actual", abs(actual - expected) < 0.2f)
            close("upper arm L", (sk.elbowL - sk.shoulderL).length(), Body.UPPER_ARM)
            close("forearm L", (sk.wristL - sk.elbowL).length(), Body.FOREARM)
            close("upper arm R", (sk.elbowR - sk.shoulderR).length(), Body.UPPER_ARM)
            close("forearm R", (sk.wristR - sk.elbowR).length(), Body.FOREARM)
            close("thigh L", (sk.kneeL - sk.hipL).length(), Body.THIGH)
            close("shin L", (sk.ankleL - sk.kneeL).length(), Body.SHIN)
            close("thigh R", (sk.kneeR - sk.hipR).length(), Body.THIGH)
            close("shin R", (sk.ankleR - sk.kneeR).length(), Body.SHIN)
        }
    }

    @Test
    fun animationsLoopSeamlesslyAndStayFinite() {
        for (e in SeedData.exercises) {
            val anim: RigAnimation = RigLibrary.animation(e.id)
            // Multi-part clips (joint circles) run several movements back to back and are allowed to be longer.
            val maxLoop = if (anim.captions.isNotEmpty()) 60_000L else 20_000L
            assertTrue("${e.id} loop length", anim.loopMs in 1200L..maxLoop)
            val a = anim.poseAt(0)
            val b = anim.poseAt(anim.loopMs)
            assertEquals("${e.id} seam", a.anchor, b.anchor)
            var t = 0L
            while (t < anim.loopMs) {
                val sk = RigSolver.solve(anim.poseAt(t))
                val values = listOf(sk.hip.y, sk.shoulder.y, sk.headCenter.z, sk.kneeL.y, sk.wristR.z, sk.toeL.y)
                assertTrue("${e.id} finite at $t", values.all { it.isFinite() })
                t += 250L
            }
        }
    }

    @Test
    fun rendersEveryExerciseWithHighlightsInBothViews() {
        for (e in SeedData.exercises) {
            val anim = RigLibrary.animation(e.id)
            for (view in ViewKind.values()) {
                val pose = anim.startPose()
                val plain = RigRenderer.render(anim.scene, pose, view)
                val withHighlights = RigRenderer.render(anim.scene, pose, view, e.highlights.toDraws())
                assertTrue("${e.id} ${view.name} highlights drawn", withHighlights.size > plain.size)
                val bounds = RigFraming.bounds(anim, view)
                assertTrue("${e.id} ${view.name} bounds", bounds.width > 30f && bounds.height > 60f)
            }
        }
    }

    @Test
    fun wrongAndRightStillsDiffer() {
        for (e in SeedData.exercises) for (dd in e.doDonts) {
            val w = RigLibrary.still(dd.wrongPoseKey) ?: continue
            val r = RigLibrary.still(dd.rightPoseKey) ?: continue
            assertTrue("${e.id}: wrong and right look identical", w.pose != r.pose)
        }
    }

    @Test
    fun exerciseSpecificPostureChecks() {
        // Locked elbows in the right straight-arm drills: the elbow is nearly collinear with shoulder and wrist.
        for (key in listOf("scapular_pullups.right_straight_arm", "scapular_dips.right_depressed", "scapular_pushups.right_protracted")) {
            val sk = RigSolver.solve(RigLibrary.still(key)!!.pose)
            val chord = (sk.wristL - sk.shoulderL).length()
            assertTrue("$key straight arm (chord $chord)", chord > Body.ARM_REACH - 1.0f)
        }
        // Wrong pull-up top has shrugged shoulders: shoulder joints sit higher than in the right one relative to the torso.
        val wrong = RigLibrary.still("strict_pullups.wrong_shrug_chin_poke")!!.pose
        val right = RigLibrary.still("strict_pullups.right_chest_to_bar")!!.pose
        assertTrue(wrong.shrug > right.shrug)
        // Pike push-up: hips above the shoulders.
        val pike = RigSolver.solve(RigLibrary.animation("pike_pushups").startPose())
        assertTrue(pike.hip.y > pike.shoulder.y + 20f)
        // L-sit: legs reach horizontally forward of the hips.
        val lsit = RigSolver.solve(RigLibrary.still("l_sit.right_depressed")!!.pose)
        assertTrue(lsit.ankleL.z - lsit.hip.z > 70f)
        assertTrue(abs(lsit.ankleL.y - lsit.hip.y) < 12f)
    }
}
