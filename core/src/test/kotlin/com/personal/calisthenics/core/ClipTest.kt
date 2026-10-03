package com.personal.calisthenics.core

import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.ClipPlayer
import com.personal.calisthenics.core.rig.OrbitSpec
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.RigRenderer
import com.personal.calisthenics.core.rig.scaledToLoop
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** M5b: the orbiting 3D clip player, camera maths and tempo scaling. */
class ClipTest {

    private fun player(id: String, orbit: OrbitSpec = OrbitSpec()) =
        ClipPlayer(RigLibrary.animation(id), SeedData.exercise(id).highlights, orbit)

    @Test
    fun cameraSwingsAroundTheCentreYawAndLoops() {
        val p = player("strict_pullups", OrbitSpec(centerYaw = -40f, sweepDeg = 30f, periodMs = 10_000))
        assertEquals(-40f, p.cameraAt(0).yaw, 1e-3f)
        assertEquals(-10f, p.cameraAt(2_500).yaw, 1e-2f)
        assertEquals(-70f, p.cameraAt(7_500).yaw, 1e-2f)
        assertEquals(p.cameraAt(1_234).yaw, p.cameraAt(11_234).yaw, 1e-3f)
        assertEquals(-25f, p.cameraAt(0, yawOffset = 15f).yaw, 1e-3f)
    }

    @Test
    fun fixedOrbitKeepsTheCameraStill() {
        val p = player("pistol_squat", OrbitSpec(centerYaw = -90f, sweepDeg = 0f))
        assertEquals(p.cameraAt(0).yaw, p.cameraAt(5_000).yaw, 1e-4f)
    }

    @Test
    fun framingHoldsTheFigureForEveryCameraAngleOfTheOrbit() {
        for (id in listOf("strict_pullups", "planche_lean", "nordic_curl", "pistol_squat")) {
            val p = player(id)
            val b = p.bounds
            for (t in 0L..p.animation.loopMs step 700L) {
                val frame = p.frame(t * 3)
                for (prim in frame.prims) {
                    if (prim.material in RigRenderer.groundMaterials) continue
                    val c = when (prim) {
                        is com.personal.calisthenics.core.rig.DiscPrim -> prim.c
                        is com.personal.calisthenics.core.rig.CapsulePrim -> prim.a
                        is com.personal.calisthenics.core.rig.EllipsePrim -> prim.c
                        is com.personal.calisthenics.core.rig.PolyPrim -> prim.points.first()
                        is com.personal.calisthenics.core.rig.LinePrim -> prim.a
                    }
                    assertTrue("$id centre outside bounds ${c.x},${c.y} vs $b", c.x >= b.minX && c.x <= b.maxX && c.y >= b.minY && c.y <= b.maxY)
                }
            }
        }
    }

    @Test
    fun anyYawBoundsAreAtLeastAsWideAsTheOrbitBounds() {
        val p = player("l_sit")
        assertTrue(p.boundsAnyYaw.width >= p.bounds.width - 1e-3f)
        assertTrue(p.boundsAnyYaw.height >= p.bounds.height - 1e-3f)
    }

    @Test
    fun framesCarryClipProgressAndRender() {
        val p = player("strict_pullups")
        val atStart = p.frame(0)
        assertEquals(0f, atStart.progress, 1e-3f)
        assertTrue(atStart.prims.isNotEmpty())
        var best = 0f
        for (i in 0..120) best = maxOf(best, p.frame(p.animation.loopMs * i / 120).progress)
        assertTrue(best > 0.97f)
    }

    @Test
    fun scalingTheLoopKeepsTheMotionAndSetsTheDuration() {
        val anim = RigLibrary.animation("nordic_curl")
        val scaled = anim.scaledToLoop(8_000)
        assertTrue(abs(scaled.loopMs - 8_000L) <= scaled.keyframes.size * 2L)
        assertEquals(anim.keyframes.size, scaled.keyframes.size)
        assertEquals(anim.poseAt(0), scaled.poseAt(0))
    }

    @Test
    fun orbitCameraLooksAtTheFigureFromTheRightSide() {
        // Yaw -90 is the side view facing right: the camera sits on the figure's left (negative x).
        val c = Camera(yaw = -90f, pitch = 0f)
        assertTrue(c.toCamera.x < -0.99f)
        assertTrue(abs(c.toCamera.z) < 1e-3f)
    }
}

/** M5d: every DO/DON'T body picture carries short arrow labels. */
class StillLabelTest {

    private val bodyStillKeys: List<String> by lazy {
        SeedData.exercises.flatMap { e -> e.doDonts.flatMap { listOf(it.wrongPoseKey, it.rightPoseKey) } }.filter { !it.startsWith("detail.") }
    }

    @Test
    fun everyBodyStillHasLabelsAndWrongOnesAreRedRightOnesGreen() {
        for (key in bodyStillKeys) {
            val labels = com.personal.calisthenics.core.rig.Annotations.forStill(key)
            assertTrue("$key has no labels", labels.isNotEmpty())
            val wrong = key.substringAfter('.').startsWith("wrong")
            assertTrue("$key colour mismatch", labels.all { it.good == !wrong })
            assertTrue("$key label too long", labels.all { it.text.length <= 30 })
        }
    }

    @Test
    fun labelsLandInsideTheSharedFrameForBothCameras() {
        for (e in SeedData.exercises) for (dd in e.doDonts) {
            if (dd.wrongPoseKey.startsWith("detail.")) continue
            for (cam in listOf(Camera.SIDE, Camera.THREE_QUARTER)) {
                val b = com.personal.calisthenics.core.rig.Stills.bounds(listOf(dd.wrongPoseKey, dd.rightPoseKey), cam)
                for (key in listOf(dd.wrongPoseKey, dd.rightPoseKey)) {
                    val frame = com.personal.calisthenics.core.rig.Stills.render(key, cam)!!
                    for (l in frame.overlay) {
                        val box = l.box()
                        assertTrue("$key label '${l.text}' outside frame", box.minX >= b.minX && box.maxX <= b.maxX && box.minY >= b.minY && box.maxY <= b.maxY)
                    }
                }
            }
        }
    }
}
