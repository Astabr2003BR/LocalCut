package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineZoomUiPolicyTest {
    @Test
    fun zoomStaysInsideMobileBounds() {
        assertEquals(
            TimelineZoomUiPolicy.MAX_DP_PER_SECOND,
            TimelineZoomUiPolicy.zoomIn(10_000f),
            0f,
        )
        assertEquals(
            TimelineZoomUiPolicy.MIN_DP_PER_SECOND,
            TimelineZoomUiPolicy.zoomOut(-10_000f),
            0f,
        )
    }

    @Test
    fun zoomMovesInDeterministicStepsAndCanReset() {
        val start = TimelineZoomUiPolicy.DEFAULT_DP_PER_SECOND
        val zoomed = TimelineZoomUiPolicy.zoomIn(start)
        assertEquals(start + TimelineZoomUiPolicy.STEP_DP_PER_SECOND, zoomed, 0f)
        assertEquals(start, TimelineZoomUiPolicy.zoomOut(zoomed), 0f)
        assertEquals(start, TimelineZoomUiPolicy.reset(), 0f)
    }

    @Test
    fun percentageTracksClampedZoom() {
        assertEquals(100, TimelineZoomUiPolicy.percent(TimelineZoomUiPolicy.DEFAULT_DP_PER_SECOND))
        assertTrue(TimelineZoomUiPolicy.percent(TimelineZoomUiPolicy.MAX_DP_PER_SECOND) > 100)
        assertTrue(TimelineZoomUiPolicy.percent(TimelineZoomUiPolicy.MIN_DP_PER_SECOND) < 100)
    }
}
