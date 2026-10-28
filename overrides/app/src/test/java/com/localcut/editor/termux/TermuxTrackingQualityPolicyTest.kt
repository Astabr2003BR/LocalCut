package com.localcut.editor.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxTrackingQualityPolicyTest {
    @Test
    fun presetsBalanceMobileCostAndPrecision() {
        assertEquals(
            TermuxTrackingOptions(maxFrames = 120, sampleEvery = 4, maxCorners = 80),
            TermuxTrackingQualityPolicy.options(TermuxTrackingQuality.FAST),
        )
        assertEquals(
            TermuxTrackingOptions(maxFrames = 180, sampleEvery = 2, maxCorners = 120),
            TermuxTrackingQualityPolicy.options(TermuxTrackingQuality.NORMAL),
        )
        assertEquals(
            TermuxTrackingOptions(maxFrames = 300, sampleEvery = 1, maxCorners = 200),
            TermuxTrackingQualityPolicy.options(TermuxTrackingQuality.PRECISE),
        )
    }

    @Test
    fun everyPresetStaysWithinProtocolLimits() {
        TermuxTrackingQuality.values().forEach { quality ->
            val options = TermuxTrackingQualityPolicy.options(quality)
            assertTrue(options.maxFrames in 2..600)
            assertTrue(options.sampleEvery in 1..30)
            assertTrue(options.maxCorners in 20..500)
        }
    }
}
