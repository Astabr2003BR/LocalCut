package com.localcut.editor.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatDetectorTest {
    @Test
    fun periodicPeaksProduceOrderedBeatMarkers() {
        val sampleRate = 100
        val samples = FloatArray(500)
        listOf(50, 150, 250, 350, 450).forEach { center ->
            for (i in -2..2) {
                val index = center + i
                if (index in samples.indices) samples[index] = 1.0f - kotlin.math.abs(i) * 0.1f
            }
        }

        val beats = BeatDetector.detect(
            monoSamples = samples,
            sampleRateHz = sampleRate,
            settings = BeatDetectionSettings(
                analysisWindowMs = 100,
                minimumIntervalMs = 600,
                thresholdMultiplier = 1.5,
            ),
        )

        assertEquals(5, beats.size)
        assertTrue(beats.zipWithNext().all { (a, b) -> a.timeUs < b.timeUs })
        assertTrue(beats.all { it.strength in 0.0..1.0 })
    }

    @Test
    fun silenceProducesNoBeats() {
        val beats = BeatDetector.detect(FloatArray(1_000), 1_000)
        assertTrue(beats.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSampleRateIsRejected() {
        BeatDetector.detect(floatArrayOf(1f), 0)
    }
}
