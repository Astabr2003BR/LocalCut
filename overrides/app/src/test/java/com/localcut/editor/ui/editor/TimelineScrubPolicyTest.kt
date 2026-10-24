package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineScrubPolicyTest {
    @Test
    fun dragMovesPlayheadUsingTimelineScale() {
        assertEquals(
            3_500_000L,
            TimelineScrubPolicy.positionForDrag(
                startUs = 2_000_000L,
                deltaDp = 75f,
                dpPerSecond = 50f,
                durationUs = 10_000_000L,
            ),
        )
    }

    @Test
    fun dragClampsAtProjectStart() {
        assertEquals(
            0L,
            TimelineScrubPolicy.positionForDrag(
                startUs = 1_000_000L,
                deltaDp = -500f,
                dpPerSecond = 50f,
                durationUs = 10_000_000L,
            ),
        )
    }

    @Test
    fun dragClampsAtProjectEnd() {
        assertEquals(
            10_000_000L,
            TimelineScrubPolicy.positionForDrag(
                startUs = 9_000_000L,
                deltaDp = 500f,
                dpPerSecond = 50f,
                durationUs = 10_000_000L,
            ),
        )
    }

    @Test
    fun viewportDragCanReachProjectStartInOneGesture() {
        assertEquals(
            0L,
            TimelineScrubPolicy.positionForViewportDrag(
                startUs = 45_000_000L,
                deltaDp = -360f,
                viewportWidthDp = 360f,
                durationUs = 70_000_000L,
            ),
        )
    }

    @Test
    fun viewportDragCanReachProjectEndInOneGesture() {
        assertEquals(
            70_000_000L,
            TimelineScrubPolicy.positionForViewportDrag(
                startUs = 20_000_000L,
                deltaDp = 360f,
                viewportWidthDp = 360f,
                durationUs = 70_000_000L,
            ),
        )
    }

    @Test
    fun tapClampsToProjectDuration() {
        assertEquals(
            10_000_000L,
            TimelineScrubPolicy.positionForTap(
                xDp = 2_000f,
                dpPerSecond = 50f,
                durationUs = 10_000_000L,
            ),
        )
    }

    @Test
    fun invalidDragDeltaKeepsCurrentPosition() {
        assertEquals(
            4_000_000L,
            TimelineScrubPolicy.positionForDrag(
                startUs = 4_000_000L,
                deltaDp = Float.NaN,
                dpPerSecond = 50f,
                durationUs = 10_000_000L,
            ),
        )
    }
}
