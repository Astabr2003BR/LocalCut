package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimelineThumbnailPolicyTest {
    @Test
    fun narrowClipUsesSingleThumbnail() {
        assertEquals(1, TimelineThumbnailPolicy.sampleCount(32f))
    }

    @Test
    fun widerClipUsesMoreSamplesButCapsAtMaximum() {
        assertEquals(2, TimelineThumbnailPolicy.sampleCount(128f))
        assertEquals(
            TimelineThumbnailPolicy.MAX_SAMPLES_PER_CLIP,
            TimelineThumbnailPolicy.sampleCount(2_000f),
        )
    }

    @Test
    fun trimmedVideoSamplesStayInsideSourceRange() {
        assertEquals(
            listOf(6_000_000L, 8_000_000L, 10_000_000L, 12_000_000L),
            TimelineThumbnailPolicy.sampleTimesUs(
                sourceInUs = 5_000_000L,
                sourceOutUs = 13_000_000L,
                clipWidthDp = 256f,
                stillImage = false,
            ),
        )
    }

    @Test
    fun stillImageNeedsOnlyOneSampleAtSourceStart() {
        assertEquals(
            listOf(2_500_000L),
            TimelineThumbnailPolicy.sampleTimesUs(
                sourceInUs = 2_500_000L,
                sourceOutUs = 9_000_000L,
                clipWidthDp = 400f,
                stillImage = true,
            ),
        )
    }

    @Test
    fun invalidSourceRangeFallsBackToSourceStart() {
        assertEquals(
            listOf(4_000_000L),
            TimelineThumbnailPolicy.sampleTimesUs(
                sourceInUs = 4_000_000L,
                sourceOutUs = 3_000_000L,
                clipWidthDp = 256f,
                stillImage = false,
            ),
        )
    }

    @Test
    fun cacheKeyIsStableAndDistinguishesFrameIdentity() {
        val base = TimelineThumbnailPolicy.cacheKey("asset-fp", 4_000_000L, 144, 80)
        assertEquals(base, TimelineThumbnailPolicy.cacheKey("asset-fp", 4_000_000L, 144, 80))
        assertNotEquals(base, TimelineThumbnailPolicy.cacheKey("asset-fp", 5_000_000L, 144, 80))
        assertNotEquals(base, TimelineThumbnailPolicy.cacheKey("other-fp", 4_000_000L, 144, 80))
    }
}
