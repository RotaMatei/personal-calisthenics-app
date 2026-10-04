package com.personal.calisthenics.core

import com.personal.calisthenics.core.model.Tempo
import com.personal.calisthenics.core.rig.ClipSync
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.RigSolver
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipSyncTest {

    private fun tempoExercises() =
        SeedData.steps.filter { it.tempo != null }.flatMap { s -> (listOf(s.exerciseId) + s.exerciseByDay.values).map { it to s.tempo!! } }.distinct()

    @Test
    fun jointCirclesPartsLineUpWithTheFiveTimerSublabels() {
        val circles = RigLibrary.animation("joint_circles")
        val step = SeedData.steps.single { it.id == "w0_circles" }
        val part = step.flowParts.single()
        val drillMs = part.seconds * 1000L
        assertEquals(5, part.subLabels.size)
        // Start of each timer slice must land on the start of the matching clip part, and its label must name that part.
        part.subLabels.indices.forEach { i ->
            val sliceStart = drillMs * i / part.subLabels.size
            val clipMs = ClipSync.drillTimeMs(circles, sliceStart + 200, drillMs)
            val expected = circles.captions[i]
            assertEquals("part $i caption at the start of its slice", expected, circles.captionAt(clipMs))
            assertTrue(part.subLabels[i].startsWith(expected.substringBefore(' ')))
        }
        assertEquals("one loop per drill", circles.loopMs, ClipSync.drillTimeMs(circles, drillMs, drillMs))
    }

    @Test
    fun theStressPulseStillMeasuresFromPositionAAtTempo() {
        var checked = 0
        for ((id, tempo) in tempoExercises()) {
            val base = RigLibrary.animationOrNull(id) ?: continue
            if (base.keyframes.size != 2 || base.captions.isNotEmpty()) continue
            val retimed = ClipSync.animationAtTempo(base, tempo)
            val second = retimed.keyframes[0].holdMs + retimed.keyframes[0].moveMs
            // Whatever keyframe the retimed clip starts with, Position A reads 0 and Position B reads 1.
            val (tA, tB) = if (retimed.keyframes[0].pose == base.startPose()) 0L to second else second to 0L
            assertEquals("$id at Position A", 0f, retimed.progressAt(tA), 1e-3f)
            assertEquals("$id at Position B", 1f, retimed.progressAt(tB), 1e-3f)
            checked++
        }
        assertTrue("checked the two-pose strength clips ($checked)", checked >= 6)
    }

    @Test
    fun otherDrillsRunAtTheirOwnSpeed() {
        val jacks = RigLibrary.animation("jumping_jacks")
        assertEquals(1234L, ClipSync.drillTimeMs(jacks, 1234L, 45_000L))
        assertEquals(0L, ClipSync.drillTimeMs(jacks, -50L, 45_000L))
    }

    @Test
    fun tempoClipLowersPausesAndDrivesLikeTheBeeps() {
        val tempo = Tempo.parse("3-1-X-0")
        val clip = ClipSync.animationAtTempo(RigLibrary.animation("strict_pullups"), tempo)
        assertEquals("one loop is one rep", tempo.repSeconds * 1000L, clip.loopMs)
        fun shoulderY(setMs: Long) = RigSolver.solve(clip.poseAt(ClipSync.tempoTimeMs(setMs, 3000L, tempo))).shoulder.y
        val top = shoulderY(0L)            // lead-in: waiting at the top
        val bottom = shoulderY(3000L + 3000L + 500L)   // lowering (3 s) done, in the 1 s pause
        val back = shoulderY(3000L + 5000L)            // full rep later: back at the top
        assertTrue("lowered during the 3 s eccentric: $top -> $bottom", top - bottom > 30f)
        assertEquals("rep 2 starts where rep 1 did", top, back, 0.5f)
        // Mid-lowering the body is between top and bottom; the drive back up is quick (1 s).
        val mid = shoulderY(3000L + 1500L)
        assertTrue(mid < top && mid > bottom)
        val driving = shoulderY(3000L + 4500L)
        assertTrue("half-way through the 1 s drive the body is already rising", driving > bottom + 5f)
    }

    @Test
    fun everyTempoExerciseStartsEachRepAtItsTop() {
        for ((id, tempo) in tempoExercises()) {
            val base = RigLibrary.animation(id)
            val clip = ClipSync.animationAtTempo(base, tempo)
            assertEquals("$id loop", tempo.repSeconds * 1000L, clip.loopMs)
            // The rep is lowered first: the centre of mass at the start of the lowering is not lower than at the bottom.
            val startMs = ClipSync.tempoTimeMs(0L, 0L, tempo)
            val bottomMs = startMs + tempo.eccentricSec * 1000L
            fun height(ms: Long): Float {
                val sk = RigSolver.solve(clip.poseAt(ms))
                return listOf(sk.shoulder, sk.hip, sk.headCenter, sk.kneeL, sk.kneeR, sk.ankleL, sk.ankleR, sk.wristL, sk.wristR)
                    .map { it.y }.average().toFloat()
            }
            assertTrue("$id: top ${height(startMs)} vs bottom ${height(bottomMs)}", height(startMs) > height(bottomMs) + 3f)
        }
    }
}
