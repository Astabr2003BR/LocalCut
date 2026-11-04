package com.localcut.editor.domain.animation

import com.localcut.editor.audio.WaveformData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformLipSyncCuePlannerTest {
    @Test
    fun speechLikePeaksProduceSortedMergedBoundedCues() {
        val waveform = WaveformData(
            peaks = listOf(0.01f, 0.02f, 0.30f, 0.80f, 0.70f, 0.02f, 0.50f, 0.01f),
        )

        val cues = WaveformLipSyncCuePlanner.plan(waveform, actionDurationUs = 800_000L)

        assertEquals(2, cues.size)
        assertEquals(200_000L, cues[0].offsetUs)
        assertEquals(300_000L, cues[0].durationUs)
        assertEquals(600_000L, cues[1].offsetUs)
        assertEquals(100_000L, cues[1].durationUs)
        assertTrue(cues.all { it.openness in 0.0..1.0 })
        assertTrue(cues.zipWithNext().all { (left, right) ->
            left.offsetUs + left.durationUs <= right.offsetUs
        })
        assertTrue(cues.all { it.offsetUs >= 0L && it.offsetUs + it.durationUs <= 800_000L })
    }

    @Test
    fun silenceAndFlatNoiseReturnNoCues() {
        assertTrue(
            WaveformLipSyncCuePlanner.plan(
                WaveformData(List(12) { 0.0f }),
                actionDurationUs = 1_200_000L,
            ).isEmpty(),
        )
        assertTrue(
            WaveformLipSyncCuePlanner.plan(
                WaveformData(List(12) { 0.18f }),
                actionDurationUs = 1_200_000L,
            ).isEmpty(),
        )
    }

    @Test
    fun oneActiveBucketProducesOneBoundedCue() {
        val cues = WaveformLipSyncCuePlanner.plan(
            WaveformData(listOf(0.80f)),
            actionDurationUs = 90_000L,
        )

        assertEquals(1, cues.size)
        assertEquals(0L, cues.single().offsetUs)
        assertEquals(90_000L, cues.single().durationUs)
        assertEquals(1.0, cues.single().openness, 1e-9)
    }

    @Test
    fun veryShortDurationNeverCreatesZeroOrOverlappingCue() {
        val cues = WaveformLipSyncCuePlanner.plan(
            WaveformData(listOf(0.0f, 0.8f, 0.7f, 0.0f)),
            actionDurationUs = 2L,
        )

        assertTrue(cues.all { it.durationUs > 0L })
        assertTrue(cues.all { it.offsetUs >= 0L && it.offsetUs + it.durationUs <= 2L })
        assertTrue(cues.zipWithNext().all { (left, right) ->
            left.offsetUs + left.durationUs <= right.offsetUs
        })
    }

    @Test
    fun normalizationKeepsLouderRunAtLeastAsOpenAsQuieterRun() {
        val cues = WaveformLipSyncCuePlanner.plan(
            WaveformData(listOf(0.01f, 0.25f, 0.01f, 0.75f, 0.01f)),
            actionDurationUs = 500_000L,
        )

        assertEquals(2, cues.size)
        assertTrue(cues[0].openness in 0.0..1.0)
        assertTrue(cues[1].openness in 0.0..1.0)
        assertTrue(cues[1].openness >= cues[0].openness)
        assertEquals(1.0, cues[1].openness, 1e-9)
    }

    @Test
    fun identicalInputIsDeterministic() {
        val waveform = WaveformData(listOf(0.01f, 0.35f, 0.60f, 0.01f, 0.50f))

        val first = WaveformLipSyncCuePlanner.plan(waveform, 750_000L)
        val second = WaveformLipSyncCuePlanner.plan(waveform, 750_000L)

        assertEquals(first, second)
    }

    @Test
    fun emptyAndNonFiniteInputFailSafe() {
        assertTrue(WaveformLipSyncCuePlanner.plan(WaveformData(emptyList()), 400_000L).isEmpty())
        assertTrue(
            WaveformLipSyncCuePlanner.plan(
                WaveformData(listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)),
                400_000L,
            ).isEmpty(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveDurationIsRejected() {
        WaveformLipSyncCuePlanner.plan(WaveformData(listOf(0.5f)), 0L)
    }
}
