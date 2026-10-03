package com.personal.calisthenics.core

import com.personal.calisthenics.core.model.BodyRegion
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.model.HighlightKind
import com.personal.calisthenics.core.model.Load
import com.personal.calisthenics.core.model.PeakAt
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.toDraws
import com.personal.calisthenics.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** M5c: the colour strength of every anatomy highlight follows its load and where in the rep it peaks. */
class StressMapTest {

    private fun h(load: Load, peak: PeakAt) = Highlight(BodyRegion.LATS, HighlightKind.MUSCLE, "", load, peak)

    private fun near(expected: Float, actual: Float) =
        assertTrue("expected $expected but was $actual", abs(expected - actual) < 1e-4f)

    @Test
    fun steadyHighlightKeepsItsLoadWeightAllTheWay() {
        for (p in listOf(0f, 0.3f, 1f)) near(Load.SECONDARY.weight, h(Load.SECONDARY, PeakAt.STEADY).strengthAt(p))
    }

    @Test
    fun startPeakFadesTowardPositionB() {
        val x = h(Load.PRIMARY, PeakAt.START)
        near(1f, x.strengthAt(0f))
        near(0.4f, x.strengthAt(1f))
        assertTrue(x.strengthAt(0.2f) > x.strengthAt(0.8f))
    }

    @Test
    fun endPeakGrowsTowardPositionB() {
        val x = h(Load.SECONDARY, PeakAt.END)
        near(0.7f * 0.4f, x.strengthAt(0f))
        near(0.7f, x.strengthAt(1f))
    }

    @Test
    fun stillPictureShowsEveryHighlightAtItsPeak() {
        near(1f, h(Load.PRIMARY, PeakAt.START).strengthAt(null))
        near(1f, h(Load.PRIMARY, PeakAt.END).strengthAt(null))
        near(0.4f, h(Load.MINOR, PeakAt.END).strengthAt(null))
    }

    @Test
    fun loadOrderingIsPrimaryAboveSecondaryAboveMinor() {
        assertTrue(Load.PRIMARY.weight > Load.SECONDARY.weight && Load.SECONDARY.weight > Load.MINOR.weight)
    }

    @Test
    fun everyExerciseHasAPrimaryLoadAndNoDuplicateRegions() {
        for (e in SeedData.exercises) {
            assertTrue("${e.id} needs a primary highlight", e.highlights.any { it.load == Load.PRIMARY })
            val regions = e.highlights.map { it.region }
            assertEquals("${e.id} lists a region twice", regions.size, regions.toSet().size)
        }
    }

    @Test
    fun clipProgressRunsFromPositionAToPositionB() {
        for (e in SeedData.exercises) {
            val anim = RigLibrary.animation(e.id)
            near(0f, anim.progressAt(0))
            var best = 0f
            for (i in 0..240) best = maxOf(best, anim.progressAt(anim.loopMs * i / 240))
            assertTrue("${e.id} never reaches Position B (max progress $best)", best > 0.97f)
        }
    }

    @Test
    fun highlightsPulseWithTheClipForPeakingRegions() {
        val pull = SeedData.exercise("strict_pullups")
        val atBottom = pull.highlights.toDraws(0f).associateBy { it.region }
        val atTop = pull.highlights.toDraws(1f).associateBy { it.region }
        // Distal biceps tendon peaks in the dead hang, lats and biceps peak at the top.
        assertTrue(atBottom.getValue(BodyRegion.DISTAL_BICEPS_TENDON).intensity > atTop.getValue(BodyRegion.DISTAL_BICEPS_TENDON).intensity)
        assertTrue(atTop.getValue(BodyRegion.LATS).intensity > atBottom.getValue(BodyRegion.LATS).intensity)
        // The patellar tendon of the pistol squat loads at the bottom of the squat.
        val pistol = SeedData.exercise("pistol_squat").highlights.toDraws(1f).associateBy { it.region }
        assertEquals(1f, pistol.getValue(BodyRegion.PATELLAR_TENDON).intensity, 1e-4f)
    }
}
