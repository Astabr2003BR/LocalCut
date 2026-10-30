package com.localcut.editor.domain.time

import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeMapEvaluatorTest {
    @Test
    fun constantTwoXMapsPresentationToSourceAndHalvesDuration() {
        val map = TimeMap(baseSpeed = 2.0)

        assertEquals(1_000_000L, TimeMapEvaluator.sourceOffsetAt(map, 500_000L, 4_000_000L))
        assertEquals(2_000_000L, TimeMapEvaluator.presentationDurationUs(4_000_000L, map))
    }

    @Test
    fun linearSpeedRampIntegratesSpeedOverPresentationTime() {
        val map = TimeMap(
            baseSpeed = 1.0,
            speedPoints = listOf(
                SpeedPoint(0L, 1.0),
                SpeedPoint(1_000_000L, 2.0),
            ),
        )

        assertEquals(1_500_000L, TimeMapEvaluator.forwardSourceOffsetAt(map, 1_000_000L))
    }

    @Test
    fun reverseMapsBeginningToSourceEndAndEndToBeginning() {
        val map = TimeMap(baseSpeed = 1.0, reverse = true)

        assertEquals(4_000_000L, TimeMapEvaluator.sourceOffsetAt(map, 0L, 4_000_000L))
        assertEquals(0L, TimeMapEvaluator.sourceOffsetAt(map, 4_000_000L, 4_000_000L))
    }

    @Test
    fun freezeHoldsSourceAndAddsPresentationDuration() {
        val map = TimeMap(
            freezeSegments = listOf(FreezeSegment(500_000L, 250_000L, 500_000L)),
        )

        assertEquals(500_000L, TimeMapEvaluator.sourceOffsetAt(map, 600_000L, 1_000_000L))
        assertEquals(750_000L, TimeMapEvaluator.sourceOffsetAt(map, 1_000_000L, 1_000_000L))
        assertEquals(1_250_000L, TimeMapEvaluator.presentationDurationUs(1_000_000L, map))
    }

    @Test
    fun rampDurationSolverFindsPresentationDurationForSourceRange() {
        val map = TimeMap(
            baseSpeed = 1.0,
            speedPoints = listOf(SpeedPoint(0L, 1.0), SpeedPoint(1_000_000L, 2.0)),
        )

        val duration = TimeMapEvaluator.presentationDurationUs(1_500_000L, map)

        assertTrue(duration in 999_998L..1_000_002L)
    }

    @Test
    fun freezeOverlapValidationDoesNotOverflowLongEndOffset() {
        val map = TimeMap(
            freezeSegments = listOf(
                FreezeSegment(startOffsetUs = 1L, durationUs = Long.MAX_VALUE, sourceOffsetUs = 0L),
                FreezeSegment(startOffsetUs = 10L, durationUs = 1L, sourceOffsetUs = 0L),
            ),
        )

        assertTrue("freeze segments must not overlap" in map.validationIssues())
    }

    @Test
    fun rampDurationSolverSaturatesHugeFreezeBudgetInsteadOfWrappingNegative() {
        val huge = 5_000_000_000_000_000_000L
        val map = TimeMap(
            speedPoints = listOf(SpeedPoint(0L, 1.0), SpeedPoint(1L, 1.0)),
            freezeSegments = listOf(
                FreezeSegment(startOffsetUs = 1_000_000L, durationUs = huge, sourceOffsetUs = 0L),
                FreezeSegment(
                    startOffsetUs = 5_000_000_000_001_000_000L,
                    durationUs = huge,
                    sourceOffsetUs = 0L,
                ),
            ),
        )

        val duration = TimeMapEvaluator.presentationDurationUs(500_000L, map)

        assertTrue(duration in 499_998L..500_002L)
    }
}
