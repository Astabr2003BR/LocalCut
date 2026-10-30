package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionBlurPolicyTest {
    @Test
    fun effectiveSamplesRespectRequestHardCapAndHistoryMemoryBudget() {
        assertEquals(1, MotionBlurPolicy.effectiveSamples(1, 1920, 1080, bytesPerPixel = 4))
        assertEquals(8, MotionBlurPolicy.effectiveSamples(64, 1280, 720, bytesPerPixel = 8))
        assertEquals(6, MotionBlurPolicy.effectiveSamples(64, 1920, 1080, bytesPerPixel = 8))
        assertEquals(3, MotionBlurPolicy.effectiveSamples(64, 3840, 2160, bytesPerPixel = 4))
        assertEquals(2, MotionBlurPolicy.effectiveSamples(64, 3840, 2160, bytesPerPixel = 8))
    }

    @Test
    fun zeroShutterAndSingleSampleAreExactPassthroughWeights() {
        assertEquals(listOf(1f), MotionBlurPolicy.weights(180.0, 1))
        assertEquals(listOf(1f, 0f, 0f, 0f), MotionBlurPolicy.weights(0.0, 4))
    }

    @Test
    fun temporalWeightsAreNormalizedAndHistoryContributionGrowsWithShutter() {
        val low = MotionBlurPolicy.weights(90.0, 4)
        val high = MotionBlurPolicy.weights(360.0, 4)

        assertEquals(1.0, low.sum().toDouble(), 1e-6)
        assertEquals(1.0, high.sum().toDouble(), 1e-6)
        assertTrue(low.zipWithNext().all { (left, right) -> left >= right })
        assertTrue(high.zipWithNext().all { (left, right) -> left >= right })
        assertTrue(high.drop(1).sum() > low.drop(1).sum())
    }

    @Test
    fun seekOrLargeTimestampGapResetsTemporalHistory() {
        assertFalse(MotionBlurPolicy.shouldResetHistory(null, 1_000_000L))
        assertFalse(MotionBlurPolicy.shouldResetHistory(1_000_000L, 1_033_333L))
        assertTrue(MotionBlurPolicy.shouldResetHistory(1_000_000L, 1_000_000L))
        assertTrue(MotionBlurPolicy.shouldResetHistory(1_000_000L, 900_000L))
        assertTrue(MotionBlurPolicy.shouldResetHistory(1_000_000L, 1_250_001L))
        assertFalse(MotionBlurPolicy.shouldResetHistory(1_000_000L, 1_250_000L))
    }

    @Test
    fun invalidInputsFailClosed() {
        assertTrue(runCatching { MotionBlurPolicy.effectiveSamples(0, 1920, 1080, 4) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.effectiveSamples(65, 1920, 1080, 4) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.effectiveSamples(8, 0, 1080, 4) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.effectiveSamples(8, 1920, -1, 4) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.effectiveSamples(8, 1920, 1080, 0) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.weights(-0.1, 4) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.weights(360.1, 4) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.weights(Double.NaN, 4) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.weights(180.0, 0) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.weights(180.0, 9) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.shouldResetHistory(-1L, 0L) }.isFailure)
        assertTrue(runCatching { MotionBlurPolicy.shouldResetHistory(0L, -1L) }.isFailure)
    }
}
