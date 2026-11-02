package com.localcut.editor.audio

import com.localcut.editor.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProcessingModelTest {
    @Test
    fun defaultsAreNeutralAndValid() {
        val settings = AudioProcessingSettings()

        assertTrue(settings.validationIssues().isEmpty())
        assertEquals(0.0, settings.pan, 0.0)
        assertEquals(0.0, settings.normalizationGainDb, 0.0)
        assertTrue(settings.eqBands.isEmpty())
    }

    @Test
    fun eqCompressorLimiterAndPanValidateBounds() {
        val settings = AudioProcessingSettings(
            eqBands = listOf(EqBand(1_000.0, 3.0, 0.7)),
            compressor = CompressorSettings(-18.0, 4.0, 10.0, 120.0, 1.0),
            limiter = LimiterSettings(-1.0, 80.0),
            pan = -0.5,
            normalizationGainDb = 2.0,
        )

        assertTrue(settings.validationIssues().isEmpty())
        assertTrue(settings.copy(pan = 2.0).validationIssues().isNotEmpty())
        assertTrue(settings.copy(eqBands = listOf(EqBand(Double.NaN, 0.0, 1.0))).validationIssues().isNotEmpty())
    }

    @Test
    fun beatMarkersRequireOrderedFiniteStrength() {
        val good = listOf(
            BeatMarker("b1", 100_000L, 0.5),
            BeatMarker("b2", 200_000L, 1.0),
        )
        assertTrue(BeatMarker.validationIssues(good).isEmpty())
        assertTrue(BeatMarker.validationIssues(good.reversed()).isNotEmpty())
        assertTrue(BeatMarker.validationIssues(listOf(BeatMarker("x", 0L, 2.0))).isNotEmpty())
    }
}
