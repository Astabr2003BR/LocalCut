package com.localcut.editor.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class DownsamplePeakAccumulatorTest {
    @Test
    fun peakDownsamplePreservesTransientWithinEachBlock() {
        val accumulator = DownsamplePeakAccumulator(
            sourceSampleRateHz = 8_000,
            targetSampleRateHz = 2_000,
            maxSamples = 100,
        )

        listOf(0.10f, -0.20f, 0.90f, 0.40f, 0.05f, -0.70f, 0.10f, 0.20f)
            .forEach(accumulator::add)

        val result = accumulator.finish()

        assertEquals(2_000, result.sampleRateHz)
        assertEquals(2, result.samples.size)
        assertEquals(0.90f, result.samples[0], 0.0001f)
        assertEquals(0.70f, result.samples[1], 0.0001f)
    }

    @Test
    fun finishFlushesPartialBlockInsteadOfDroppingLastTransient() {
        val accumulator = DownsamplePeakAccumulator(
            sourceSampleRateHz = 8_000,
            targetSampleRateHz = 2_000,
            maxSamples = 100,
        )

        listOf(0.10f, 0.80f, 0.20f).forEach(accumulator::add)
        val result = accumulator.finish()

        assertEquals(1, result.samples.size)
        assertEquals(0.80f, result.samples.single(), 0.0001f)
    }
}
