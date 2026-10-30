package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameExposurePolicyTest {
    @Test
    fun exposureSnapsToFrameAndUsesRequestedFrameCount() {
        val one = FrameExposurePolicy.window(playheadUs = 419_999L, fps = 25, frameCount = 1)
        val three = FrameExposurePolicy.window(playheadUs = 419_999L, fps = 25, frameCount = 3)

        assertEquals(400_000L, one.startUs)
        assertEquals(40_000L, one.durationUs)
        assertEquals(400_000L, three.startUs)
        assertEquals(120_000L, three.durationUs)
    }

    @Test
    fun nonDivisorFpsDoesNotAccumulateDurationDrift() {
        val exposure = FrameExposurePolicy.window(playheadUs = 33_333L, fps = 30, frameCount = 2)

        assertEquals(0L, exposure.startUs)
        assertEquals(66_666L, exposure.durationUs)
    }

    @Test
    fun invalidExposureFailsClosed() {
        assertTrue(runCatching { FrameExposurePolicy.window(0L, 25, 0) }.isFailure)
        assertTrue(runCatching { FrameExposurePolicy.window(0L, 25, 25) }.isFailure)
        assertTrue(runCatching { FrameExposurePolicy.window(0L, 0, 1) }.isFailure)
    }
}
