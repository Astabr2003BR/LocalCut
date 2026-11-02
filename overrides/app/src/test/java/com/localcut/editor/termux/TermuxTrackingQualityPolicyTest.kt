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
    fun objectTrackingPresetsReuseBoundedCostAndPreserveRoiAndTrim() {
        val roi = TermuxObjectTrackingRoi(0.1, 0.2, 0.3, 0.4)
        val options = TermuxTrackingQualityPolicy.objectTrackingOptions(
            quality = TermuxTrackingQuality.PRECISE,
            roi = roi,
            sourceInUs = 250_000L,
            sourceOutUs = 1_500_000L,
        )

        assertEquals(roi, options.roi)
        assertEquals(250_000L, options.sourceInUs)
        assertEquals(1_500_000L, options.sourceOutUs)
        assertEquals(300, options.maxFrames)
        assertEquals(1, options.sampleEvery)
        assertEquals(200, options.maxCorners)
    }

    @Test
    fun everyPresetStaysWithinProtocolLimits() {
        TermuxTrackingQuality.values().forEach { quality ->
            val options = TermuxTrackingQualityPolicy.options(quality)
            assertTrue(options.maxFrames in 2..600)
            assertTrue(options.sampleEvery in 1..30)
            assertTrue(options.maxCorners in 20..500)

            val objectOptions = TermuxTrackingQualityPolicy.objectTrackingOptions(
                quality = quality,
                roi = TermuxObjectTrackingRoi(0.1, 0.1, 0.2, 0.2),
                sourceInUs = 0L,
                sourceOutUs = 1_000_000L,
            )
            assertTrue(objectOptions.maxFrames in 2..600)
            assertTrue(objectOptions.sampleEvery in 1..30)
            assertTrue(objectOptions.maxCorners in 20..500)
        }
    }
}
