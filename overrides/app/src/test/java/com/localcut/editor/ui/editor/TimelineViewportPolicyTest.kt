package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineViewportPolicyTest {
    @Test
    fun playheadInsideSafeAreaKeepsCurrentScroll() {
        assertEquals(
            400,
            TimelineViewportPolicy.targetScrollPx(
                playheadPx = 520f,
                currentScrollPx = 400,
                viewportWidthPx = 360,
                maxScrollPx = 5_000,
                edgePaddingPx = 40,
            ),
        )
    }

    @Test
    fun playheadBeforeVisibleSafeAreaScrollsBackTowardProjectStart() {
        assertEquals(
            0,
            TimelineViewportPolicy.targetScrollPx(
                playheadPx = 0f,
                currentScrollPx = 2_400,
                viewportWidthPx = 360,
                maxScrollPx = 5_000,
                edgePaddingPx = 40,
            ),
        )
    }

    @Test
    fun playheadAfterVisibleSafeAreaScrollsForward() {
        assertEquals(
            880,
            TimelineViewportPolicy.targetScrollPx(
                playheadPx = 1_200f,
                currentScrollPx = 400,
                viewportWidthPx = 360,
                maxScrollPx = 5_000,
                edgePaddingPx = 40,
            ),
        )
    }

    @Test
    fun scrollTargetNeverExceedsMaximum() {
        assertEquals(
            5_000,
            TimelineViewportPolicy.targetScrollPx(
                playheadPx = 6_000f,
                currentScrollPx = 4_800,
                viewportWidthPx = 360,
                maxScrollPx = 5_000,
                edgePaddingPx = 40,
            ),
        )
    }

    @Test
    fun invalidPlayheadKeepsCurrentScroll() {
        assertEquals(
            420,
            TimelineViewportPolicy.targetScrollPx(
                playheadPx = Float.NaN,
                currentScrollPx = 420,
                viewportWidthPx = 360,
                maxScrollPx = 5_000,
                edgePaddingPx = 40,
            ),
        )
    }
}
