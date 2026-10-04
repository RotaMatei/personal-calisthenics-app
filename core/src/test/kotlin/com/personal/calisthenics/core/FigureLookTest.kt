package com.personal.calisthenics.core

import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.CapsulePrim
import com.personal.calisthenics.core.rig.DiscPrim
import com.personal.calisthenics.core.rig.EllipsePrim
import com.personal.calisthenics.core.rig.Layer
import com.personal.calisthenics.core.rig.Material
import com.personal.calisthenics.core.rig.Palette
import com.personal.calisthenics.core.rig.RenderOptions
import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.RigRenderer
import com.personal.calisthenics.core.rig.Shading
import com.personal.calisthenics.core.rig.WashPrim
import com.personal.calisthenics.core.rig.toDraws
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** M16: tension is a soft hue wash over the body (never a blob or a coloured joint), and the figure is shaded. */
class FigureLookTest {

    private val loadMaterials = setOf(Material.MUSCLE, Material.TENDON, Material.JOINT)

    private fun render(id: String, camera: Camera, progress: Float? = null): List<com.personal.calisthenics.core.rig.Prim> {
        val anim = RigLibrary.animation(id)
        val pose = anim.endPose()
        val draws = SeedData.exercise(id).highlights.toDraws(progress)
        return RigRenderer.render(anim.scene, pose, camera, draws)
    }

    private fun saturation(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        return if (mx == 0) 0f else (mx - mn) / mx.toFloat()
    }

    @Test
    fun loadColoursAreOnlyEverUsedByWashesNeverByShapesOnTheBody() {
        for (id in listOf("strict_pullups", "pistol_squat", "planche_lean", "parallel_bar_dips", "nordic_curl")) {
            for (camera in listOf(Camera.THREE_QUARTER, Camera.SIDE, Camera.FRONT, Camera(yaw = 150f))) {
                val prims = render(id, camera)
                assertTrue("$id has no wash at all", prims.any { it is WashPrim })
                for (p in prims) {
                    if (p is WashPrim) continue
                    assertFalse("$id draws a $p in a load colour", p.material in loadMaterials && (p is DiscPrim || p is CapsulePrim || p is EllipsePrim))
                }
            }
        }
    }

    @Test
    fun washHuesAreMutedRoseTealAndSand() {
        for (m in loadMaterials) {
            val c = Palette.argb(m, Layer.FILL, 1f, 1f)
            assertTrue("$m is too saturated", saturation(c) <= 0.62f)
        }
        // Rose is reddish, teal is greenish-blue, sand is yellowish but pale.
        fun ch(m: Material, shift: Int) = (Palette.argb(m, Layer.FILL, 1f, 1f) shr shift) and 0xFF
        assertTrue(ch(Material.MUSCLE, 16) > ch(Material.MUSCLE, 8) + 40)
        assertTrue(ch(Material.TENDON, 8) > ch(Material.TENDON, 16) + 40)
        assertTrue(ch(Material.JOINT, 0) > 120)
    }

    @Test
    fun washesStayGentleAndGrowWithLoad() {
        val id = "strict_pullups"
        val anim = RigLibrary.animation(id)
        fun peak(progress: Float): Float =
            RigRenderer.render(anim.scene, anim.endPose(), Camera.THREE_QUARTER, SeedData.exercise(id).highlights.toDraws(progress))
                .filterIsInstance<WashPrim>()
                .filter { it.material in loadMaterials }
                .maxOf { it.alpha }
        val atStart = peak(0f)
        val atEnd = peak(1f)
        assertTrue("wash should grow with the movement ($atStart -> $atEnd)", atEnd > atStart)
        assertTrue("washes must stay soft", atEnd <= 0.6f)
    }

    @Test
    fun washesAreClippedToTheBodyPartTheySitOn() {
        val prims = render("strict_pullups", Camera.THREE_QUARTER, 1f)
        for (w in prims.filterIsInstance<WashPrim>()) {
            assertTrue(w.clip.isNotEmpty())
            assertTrue(w.discs.isNotEmpty())
            for (poly in w.clip) assertTrue(poly.size >= 3)
        }
    }

    @Test
    fun theBodyIsShadedWithALightSideAndAShadowSide() {
        val prims = render("strict_pullups", Camera.THREE_QUARTER)
        val shaded = prims.filter { Shading.shadeOf(it) != null }
        assertTrue("limbs and head should carry gradients", shaded.size > 20)
        for (p in shaded.take(40)) {
            val colors = Shading.shadeOf(p)!!.colors
            fun luma(c: Int) = ((c shr 16) and 0xFF) * 0.3f + ((c shr 8) and 0xFF) * 0.6f + (c and 0xFF) * 0.1f
            assertTrue(luma(colors[0]) > luma(colors[1]) && luma(colors[1]) > luma(colors[2]))
        }
    }

    @Test
    fun muscleDefinitionCanBeSwitchedOffAndFollowsTheFacingSide() {
        val anim = RigLibrary.animation("planche_lean")
        fun washes(camera: Camera, definition: Boolean) =
            RigRenderer.render(anim.scene, anim.startPose(), camera, emptyList(), RenderOptions(definition = definition)).filterIsInstance<WashPrim>()
        // The torso's overall light/shadow gradient is always there; definition adds creases and muscle bellies on top.
        for (camera in listOf(Camera.THREE_QUARTER, Camera(yaw = -20f), Camera(yaw = 160f))) {
            val off = washes(camera, false).size
            val on = washes(camera, true).size
            assertTrue("definition adds washes for yaw ${camera.yaw}", on > off)
        }
        val frontCreases = washes(Camera(yaw = -20f), true).count { it.material == Material.SHADOW } -
            washes(Camera(yaw = -20f), false).count { it.material == Material.SHADOW }
        val backGrooves = washes(Camera(yaw = 160f), true).count { it.material == Material.SHADOW } -
            washes(Camera(yaw = 160f), false).count { it.material == Material.SHADOW }
        assertTrue(frontCreases > 0 && backGrooves > 0)
    }

    @Test
    fun everyAnimationStillRendersWithWashesWithoutFailing() {
        for (id in RigLibrary.animationIds) {
            val anim: RigAnimation = RigLibrary.animation(id)
            val hl = SeedData.exerciseOrNull(id)?.highlights.orEmpty()
            val frame = RigRenderer.render(anim.scene, anim.poseAt(anim.loopMs / 3), Camera.THREE_QUARTER, hl.toDraws(0.7f))
            assertTrue(id, frame.isNotEmpty())
            assertEquals(id, frame.size, frame.size)
        }
    }
}
