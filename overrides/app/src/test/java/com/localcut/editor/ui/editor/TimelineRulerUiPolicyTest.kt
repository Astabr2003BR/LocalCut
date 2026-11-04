package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineRulerUiPolicyTest {
    @Test
    fun majorTickIntervalKeepsReadableScreenSpacingAcrossZoomLevels() {
        val durationUs = 60_000_000L

        val minZoomInterval = TimelineRulerUiPolicy.majorTickIntervalUs(
            durationUs = durationUs,
            dpPerSecond = TimelineZoomUiPolicy.MIN_DP_PER_SECOND,
        )
        val maxZoomInterval = TimelineRulerUiPolicy.majorTickIntervalUs(
            durationUs = durationUs,
            dpPerSecond = TimelineZoomUiPolicy.MAX_DP_PER_SECOND,
        )

        assertTrue(minZoomInterval > maxZoomInterval)
        assertTrue(minZoomInterval / 1_000_000.0 * TimelineZoomUiPolicy.MIN_DP_PER_SECOND >= 56.0)
        assertTrue(maxZoomInterval / 1_000_000.0 * TimelineZoomUiPolicy.MAX_DP_PER_SECOND >= 56.0)
    }

    @Test
    fun longProjectsBoundMajorTickCount() {
        val durationUs = 12L * 60L * 60L * 1_000_000L
        val intervalUs = TimelineRulerUiPolicy.majorTickIntervalUs(
            durationUs = durationUs,
            dpPerSecond = TimelineZoomUiPolicy.MIN_DP_PER_SECOND,
        )

        val tickCount = durationUs / intervalUs + 1L
        assertTrue(tickCount <= TimelineRulerUiPolicy.MAX_MAJOR_TICKS + 1L)
    }

    @Test
    fun majorTicksIncludeZeroAndProjectEndCeiling() {
        val ticks = TimelineRulerUiPolicy.majorTicksUs(
            durationUs = 3_400_000L,
            dpPerSecond = 84f,
        )

        assertEquals(0L, ticks.first())
        assertTrue(ticks.last() <= 3_400_000L)
        assertTrue(ticks.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun timeLabelsStayCompactAndDeterministic() {
        assertEquals("0:00", TimelineRulerUiPolicy.formatTimeUs(0L))
        assertEquals("1:05", TimelineRulerUiPolicy.formatTimeUs(65_000_000L))
        assertEquals("1:01:01", TimelineRulerUiPolicy.formatTimeUs(3_661_000_000L))
    }
}
