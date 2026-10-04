package com.personal.calisthenics.core

import com.personal.calisthenics.core.mesh.HumanMesh
import com.personal.calisthenics.core.mesh.MeshFrames
import com.personal.calisthenics.core.mesh.MeshOptions
import com.personal.calisthenics.core.mesh.MeshRenderer
import com.personal.calisthenics.core.mesh.ViewMap
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.ClipPlayer
import com.personal.calisthenics.core.rig.RigFraming
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M22: the real human mesh (MakeHuman, CC0): loading, skinning by the rig, rendering and framing. */
class HumanMeshTest {

    private val mesh: HumanMesh = HumanMesh.fromResource() ?: error("mesh/human.bin is missing from the resources")

    @Test
    fun theBundledMeshLoadsWithSaneProportions() {
        assertTrue("detailed mesh", mesh.vertexCount > 10_000 && mesh.triangleCount > 20_000)
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (v in 0 until mesh.vertexCount) {
            minY = minOf(minY, mesh.rest[v * 3 + 1])
            maxY = maxOf(maxY, mesh.rest[v * 3 + 1])
        }
        assertEquals(0f, minY, 0.5f)
        assertEquals(177f, maxY, 1f)
        for (i in mesh.triangles) assertTrue(i in 0 until mesh.vertexCount)
    }

    @Test
    fun everyVertexIsFullyAssignedToBones() {
        for (v in 0 until mesh.vertexCount) {
            assertEquals("vertex $v", 1f, mesh.weightSum(v), 0.01f)
        }
        for (part in HumanMesh.Part.values()) {
            val w = mesh.partWeights(part)
            assertTrue("$part has vertices", w.count { it > 0.5f } > 200)
        }
    }

    @Test
    fun renderingIsDeterministicAndDrawsTheFigureAndFloor() {
        val renderer = MeshRenderer(mesh)
        val anim = RigLibrary.animation("strict_pullups")
        val view = ViewMap(MeshFrames.framing(ClipPlayer(anim, emptyList()).bounds), 240f, 300f, pad = 6f)
        val a = IntArray(240 * 300)
        val b = IntArray(240 * 300)
        val cam = Camera.THREE_QUARTER
        MeshFrames.clip(renderer, ClipPlayer(anim, emptyList()), 0L, cam, view, a)
        MeshFrames.clip(renderer, ClipPlayer(anim, emptyList()), 0L, cam, view, b)
        assertTrue("same input, same picture", a.contentEquals(b))
        val covered = a.count { (it ushr 24) != 0 }
        assertTrue("figure and floor cover part of the picture ($covered px)", covered > 240 * 300 / 12)
        assertTrue("background stays empty", covered < 240 * 300)
    }

    @Test
    fun stressHighlightsTintTheClayAndStaySoft() {
        val renderer = MeshRenderer(mesh)
        val anim = RigLibrary.animation("strict_pullups")
        val view = ViewMap(MeshFrames.framing(ClipPlayer(anim, emptyList()).bounds), 240f, 300f, pad = 6f)
        val plain = IntArray(240 * 300)
        val tinted = IntArray(240 * 300)
        val cam = Camera.THREE_QUARTER
        MeshFrames.clip(renderer, ClipPlayer(anim, emptyList()), 0L, cam, view, plain)
        MeshFrames.clip(renderer, ClipPlayer(anim, SeedData.exercise("strict_pullups").highlights), 0L, cam, view, tinted)
        val changed = plain.indices.count { plain[it] != tinted[it] }
        assertTrue("highlights change the picture ($changed px)", changed > 300)
    }

    @Test
    fun theSupersampledStillMatchesTheSizeAndIsSmootherThanTheLiveOne() {
        val renderer = MeshRenderer(mesh)
        val key = RigLibrary.stillKeys.first()
        val bounds = com.personal.calisthenics.core.rig.Stills.bounds(listOf(key), Camera.SIDE)
        val view = ViewMap(bounds, 200f, 260f, pad = 4f)
        val out = IntArray(200 * 260)
        assertTrue(MeshFrames.still(renderer, key, Camera.SIDE, emptyList(), view, out, MeshOptions(supersample = 2)))
        assertTrue(out.any { (it ushr 24) == 0xFF })
        assertTrue("silhouette edges are anti-aliased", out.any { (it ushr 24) in 1..254 })
        assertFalse(MeshFrames.still(renderer, "no.such.still", Camera.SIDE, emptyList(), view, out))
    }

    @Test
    fun theMeshFollowsEveryExercisePoseAndStaysInsideTheFraming() {
        val renderer = MeshRenderer(mesh)
        var worst = -Float.MAX_VALUE
        var worstId = ""
        for (id in RigLibrary.animationIds.sorted()) {
            val anim = RigLibrary.animation(id)
            val frame = MeshFrames.framing(ClipPlayer(anim, emptyList()).boundsAnyYaw)
            for (f in 0..3) {
                val pose = anim.poseAt(anim.loopMs * f / 4)
                for (yaw in listOf(-70f, -38f, 0f, 60f, 135f, 220f, 300f)) {
                    val cam = Camera(yaw = yaw, pitch = 12f, perspective = 0.3f)
                    val b = renderer.bodyBounds(anim.scene, pose, cam)
                    assertTrue("$id finite", b.minX.isFinite() && b.maxY.isFinite() && b.width > 20f && b.height > 20f)
                    val over = maxOf(frame.minX - b.minX, frame.minY - b.minY, b.maxX - frame.maxX, b.maxY - frame.maxY)
                    if (over > worst) { worst = over; worstId = "$id yaw=$yaw f=$f" }
                }
            }
        }
        println("tightest fit of the mesh inside the framing: ${"%.1f".format(-worst)} cm to spare ($worstId)")
        assertTrue("mesh has only ${-worst} cm to spare in the framing ($worstId)", worst <= -2f)
    }

    @Test
    fun theThumbnailFramingOfPositionAHoldsTheMesh() {
        val renderer = MeshRenderer(mesh)
        var tightest = Float.MAX_VALUE
        var tightestId = ""
        for (id in RigLibrary.animationIds.sorted()) {
            val anim = RigLibrary.animation(id)
            val cam = ClipPlayer(anim, emptyList()).cameraAt(0L)
            val frame = MeshFrames.framing(RigFraming.boundsAt(anim, listOf(0L), cam))
            val b = renderer.bodyBounds(anim.scene, anim.poseAt(0L), cam)
            val spare = minOf(b.minX - frame.minX, b.minY - frame.minY, frame.maxX - b.maxX, frame.maxY - b.maxY)
            if (spare < tightest) { tightest = spare; tightestId = id }
        }
        println("thumbnail framing: tightest ${"%.1f".format(tightest)} cm to spare ($tightestId)")
        assertTrue("mesh has only $tightest cm to spare in a thumbnail ($tightestId)", tightest >= 1f)
    }

    @Test
    fun skinningDoesNotTearOrCollapseTheBodyInAnyExercisePose() {
        val renderer = MeshRenderer(mesh)
        val tris = mesh.triangles
        val rest = mesh.rest
        fun len(p: FloatArray, a: Int, b: Int): Float {
            val dx = p[a * 3] - p[b * 3]
            val dy = p[a * 3 + 1] - p[b * 3 + 1]
            val dz = p[a * 3 + 2] - p[b * 3 + 2]
            return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        }
        var worstShare = 0f
        var worstShareId = ""
        var worstStretch = 0f
        for (id in RigLibrary.animationIds.sorted()) {
            val anim = RigLibrary.animation(id)
            for (f in 0..4) {
                val p = renderer.skinnedPositions(anim.scene, anim.poseAt(anim.loopMs * f / 4))
                var edges = 0
                var bad = 0
                for (i in 0 until mesh.triangleCount) {
                    for (e in 0 until 3) {
                        val a = tris[i * 3 + e]
                        val b = tris[i * 3 + (e + 1) % 3]
                        val r = len(rest, a, b)
                        if (r < 0.15f) continue // skip degenerate rest edges
                        val ratio = len(p, a, b) / r
                        assertTrue("$id: finite", ratio.isFinite())
                        edges++
                        if (ratio > 3f || ratio < 0.25f) bad++
                        worstStretch = maxOf(worstStretch, ratio)
                    }
                }
                val share = bad.toFloat() / edges
                if (share > worstShare) { worstShare = share; worstShareId = "$id f=$f" }
            }
        }
        println("edges stretched >3x or squashed <0.25x: at most ${"%.2f".format(worstShare * 100)}% of a pose ($worstShareId), longest stretch ${"%.1f".format(worstStretch)}x")
        // Linear-blend skinning always folds a little in the joint creases (fingers, armpits, groin); a tear shows up as many bad edges.
        assertTrue("too many torn or collapsed edges: ${worstShare * 100}% ($worstShareId)", worstShare < 0.006f)
    }

    @Test
    fun aStandingFigureHasTheRigsHeight() {
        val renderer = MeshRenderer(mesh)
        val anim = RigLibrary.animation("strict_pullups")
        val b = renderer.bodyBounds(anim.scene, anim.startPose(), Camera.FRONT)
        assertTrue("height ${b.height}", b.height > 150f)
    }
}
