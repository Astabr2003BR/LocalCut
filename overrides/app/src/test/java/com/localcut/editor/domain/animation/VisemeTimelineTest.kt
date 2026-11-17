package com.localcut.editor.domain.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisemeTimelineTest {
    @Test
    fun emptyTimelineAndSilenceReturnRest() {
        val empty = VisemeTimeline(emptyList(), durationUs = 0L)
        assertEquals(VisemePose.REST, empty.poseAt(0L))
        val timeline = VisemeTimeline(listOf(VisemeCue(100_000L, 200_000L, VisemeShape.A)), 600_000L)
        assertEquals(VisemePose.REST, timeline.poseAt(0L))
        assertEquals(VisemePose.REST, timeline.poseAt(99_999L))
        assertEquals(VisemePose.REST, timeline.poseAt(300_000L))
        assertEquals(VisemePose.REST, timeline.poseAt(600_000L))
        assertEquals(VisemePose.REST, timeline.poseAt(Long.MAX_VALUE))
    }

    @Test
    fun nineDistinctMouthShapesHaveBoundedFiniteParameters() {
        assertEquals(9, VisemeShape.entries.size)
        VisemeShape.entries.forEach { shape ->
            val sample = VisemeTimeline(listOf(VisemeCue(0L, 300_000L, shape)), 300_000L)
                .poseAt(150_000L)
            assertTrue(sample.openness in 0.0..1.0 && sample.openness.isFinite())
            assertTrue(sample.widthScale in 0.60..1.25 && sample.widthScale.isFinite())
            assertTrue(sample.rounding in 0.0..1.0 && sample.rounding.isFinite())
            assertTrue(sample.teethVisibility in 0.0..1.0 && sample.teethVisibility.isFinite())
        }
    }

    @Test
    fun vowelsAndConsonantsProduceDifferentArticulation() {
        val timeline = VisemeTimeline(
            listOf(
                VisemeCue(0L, 150_000L, VisemeShape.M_B_P),
                VisemeCue(150_000L, 150_000L, VisemeShape.A),
                VisemeCue(300_000L, 150_000L, VisemeShape.O),
                VisemeCue(450_000L, 150_000L, VisemeShape.F_V),
            ),
            600_000L,
        )
        val closed = timeline.poseAt(75_000L)
        val open = timeline.poseAt(225_000L)
        val round = timeline.poseAt(375_000L)
        val teeth = timeline.poseAt(525_000L)
        assertEquals(0.0, closed.openness, 1e-9)
        assertTrue(closed.widthScale < 1.0)
        assertTrue(open.openness > 0.8)
        assertTrue(round.rounding > 0.8)
        assertTrue(teeth.teethVisibility > 0.6)
    }

    @Test
    fun onsetAndOffsetFeatherWithoutChangingTheSilence() {
        val timeline = VisemeTimeline(listOf(VisemeCue(100_000L, 200_000L, VisemeShape.A)), 500_000L)
        val start = timeline.poseAt(100_000L)
        val ramp = timeline.poseAt(120_000L)
        val peak = timeline.poseAt(180_000L)
        val tail = timeline.poseAt(280_000L)
        assertEquals(VisemePose.REST, start)
        assertTrue(ramp.openness > 0.0 && ramp.openness < peak.openness)
        assertTrue(tail.openness in 0.0..peak.openness)
        assertEquals(VisemePose.REST, timeline.poseAt(300_000L))
    }

    @Test
    fun strengthZeroAndOneMicrosecondCuesStayFinite() {
        val quiet = VisemeTimeline(listOf(VisemeCue(0L, 100_000L, VisemeShape.A, 0.0)), 100_000L)
        assertEquals(VisemePose.REST, quiet.poseAt(50_000L))
        val short = VisemeTimeline(listOf(VisemeCue(0L, 1L, VisemeShape.A)), 1L)
        assertTrue(short.poseAt(0L).openness in 0.0..1.0)
        assertEquals(VisemePose.REST, short.poseAt(1L))
    }

    @Test
    fun timelineCopiesInputAndProducesDeterministicSamples() {
        val input = mutableListOf(VisemeCue(0L, 300_000L, VisemeShape.E_I))
        val timeline = VisemeTimeline(input, 300_000L)
        val first = timeline.poseAt(150_000L)
        input.clear()
        assertEquals(first, timeline.poseAt(150_000L))
    }

    @Test
    fun contiguousSoundsBlendWithoutClosingAtEveryBoundary() {
        val timeline = VisemeTimeline(
            listOf(
                VisemeCue(100_000L, 200_000L, VisemeShape.A),
                VisemeCue(300_000L, 200_000L, VisemeShape.O),
                VisemeCue(500_000L, 200_000L, VisemeShape.U),
            ),
            durationUs = 800_000L,
        )
        val beforeTransition = timeline.poseAt(299_999L)
        val atTransition = timeline.poseAt(300_000L)
        val afterTransition = timeline.poseAt(320_000L)
        assertTrue(beforeTransition.openness > 0.80)
        assertEquals(VisemeShape.A.openness, atTransition.openness, 1e-9)
        assertTrue(afterTransition.openness in VisemeShape.O.openness..VisemeShape.A.openness)
        assertTrue(afterTransition.rounding > 0.0)
        assertEquals(VisemeShape.O.openness, timeline.poseAt(500_000L).openness, 1e-9)
        assertEquals(VisemePose.REST, timeline.poseAt(700_000L))
    }

    @Test
    fun gapBetweenSoundsReturnsToSilenceInsteadOfHoldingPreviousShape() {
        val timeline = VisemeTimeline(
            listOf(
                VisemeCue(0L, 100_000L, VisemeShape.A),
                VisemeCue(140_000L, 100_000L, VisemeShape.E_I),
            ),
            durationUs = 300_000L,
        )
        assertEquals(VisemePose.REST, timeline.poseAt(120_000L))
        assertEquals(VisemePose.REST, timeline.poseAt(140_000L))
        assertTrue(timeline.poseAt(190_000L).openness > 0.0)
        assertEquals(VisemePose.REST, timeline.poseAt(240_000L))
    }

    @Test
    fun invalidCueFieldsAreRejected() {
        val bad = listOf<() -> Any>(
            { VisemeCue(-1L, 1L, VisemeShape.A) },
            { VisemeCue(0L, 0L, VisemeShape.A) },
            { VisemeCue(0L, -1L, VisemeShape.A) },
            { VisemeCue(Long.MAX_VALUE, 1L, VisemeShape.A) },
            { VisemeCue(0L, 1L, VisemeShape.A, Double.NaN) },
            { VisemeCue(0L, 1L, VisemeShape.A, Double.POSITIVE_INFINITY) },
            { VisemeCue(0L, 1L, VisemeShape.A, -0.1) },
            { VisemeCue(0L, 1L, VisemeShape.A, 1.1) },
        )
        bad.forEach { construct -> assertTrue(runCatching(construct).isFailure) }
    }

    @Test
    fun overlapsUnsortedAndOutOfRangeTimeAreRejected() {
        val first = VisemeCue(100L, 100L, VisemeShape.A)
        val overlaps = VisemeCue(199L, 100L, VisemeShape.O)
        val reversed = VisemeCue(0L, 20L, VisemeShape.E_I)
        assertTrue(runCatching { VisemeTimeline(listOf(first, overlaps), 500L) }.isFailure)
        assertTrue(runCatching { VisemeTimeline(listOf(first, reversed), 500L) }.isFailure)
        assertTrue(runCatching { VisemeTimeline(listOf(first), 199L) }.isFailure)
        assertTrue(runCatching { VisemeTimeline(emptyList(), -1L) }.isFailure)
        assertTrue(runCatching { VisemeTimeline(emptyList(), 10L).poseAt(-1L) }.isFailure)
        assertTrue(runCatching { VisemeTimeline(listOf(first, first), 500L) }.isFailure)
    }
}
