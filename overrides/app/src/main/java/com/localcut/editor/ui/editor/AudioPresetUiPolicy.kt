package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.EqBand
import com.localcut.editor.domain.model.LimiterSettings
import com.localcut.editor.domain.model.NoiseGateSettings

enum class AudioPreset {
    CLEAN_VOICE,
    PODCAST,
    BASS_BOOST,
    MUSIC_PUNCH,
    CINEMATIC,
}

object AudioPresetUiPolicy {
    fun label(preset: AudioPreset): String = when (preset) {
        AudioPreset.CLEAN_VOICE -> "Voz limpa"
        AudioPreset.PODCAST -> "Podcast"
        AudioPreset.BASS_BOOST -> "Bass Boost"
        AudioPreset.MUSIC_PUNCH -> "Música"
        AudioPreset.CINEMATIC -> "Cinema"
    }

    fun apply(source: AudioProcessingSettings, preset: AudioPreset): AudioProcessingSettings {
        val processed = when (preset) {
            AudioPreset.CLEAN_VOICE -> source.copy(
                normalizationGainDb = 1.5,
                eqBands = listOf(
                    EqBand(120.0, -3.0, 0.8),
                    EqBand(1_000.0, 0.5, 1.0),
                    EqBand(8_000.0, 2.0, 0.9),
                ),
                compressor = CompressorSettings(
                    thresholdDb = -20.0,
                    ratio = 3.0,
                    attackMs = 8.0,
                    releaseMs = 120.0,
                    makeupGainDb = 2.0,
                ),
                limiter = LimiterSettings(ceilingDb = -1.0),
                noiseGate = NoiseGateSettings(
                    thresholdDb = -52.0,
                    attackMs = 5.0,
                    releaseMs = 140.0,
                ),
            )
            AudioPreset.PODCAST -> source.copy(
                normalizationGainDb = 2.0,
                eqBands = listOf(
                    EqBand(120.0, -2.0, 0.9),
                    EqBand(1_000.0, 1.5, 1.1),
                    EqBand(8_000.0, 1.0, 0.8),
                ),
                compressor = CompressorSettings(
                    thresholdDb = -18.0,
                    ratio = 4.0,
                    attackMs = 10.0,
                    releaseMs = 160.0,
                    makeupGainDb = 2.5,
                ),
                limiter = LimiterSettings(ceilingDb = -1.0),
                noiseGate = NoiseGateSettings(
                    thresholdDb = -48.0,
                    attackMs = 4.0,
                    releaseMs = 180.0,
                ),
            )
            AudioPreset.BASS_BOOST -> source.copy(
                normalizationGainDb = 0.5,
                eqBands = listOf(
                    EqBand(120.0, 5.0, 0.75),
                    EqBand(1_000.0, -1.0, 1.0),
                    EqBand(8_000.0, 0.5, 0.9),
                ),
                compressor = CompressorSettings(
                    thresholdDb = -16.0,
                    ratio = 2.5,
                    attackMs = 18.0,
                    releaseMs = 180.0,
                    makeupGainDb = 1.0,
                ),
                limiter = LimiterSettings(ceilingDb = -0.8),
                noiseGate = null,
            )
            AudioPreset.MUSIC_PUNCH -> source.copy(
                normalizationGainDb = 1.0,
                eqBands = listOf(
                    EqBand(120.0, 2.0, 0.8),
                    EqBand(1_000.0, 0.5, 1.0),
                    EqBand(8_000.0, 1.5, 0.8),
                ),
                compressor = CompressorSettings(
                    thresholdDb = -14.0,
                    ratio = 2.0,
                    attackMs = 22.0,
                    releaseMs = 140.0,
                    makeupGainDb = 1.0,
                ),
                limiter = LimiterSettings(ceilingDb = -0.8),
                noiseGate = null,
            )
            AudioPreset.CINEMATIC -> source.copy(
                normalizationGainDb = 0.0,
                eqBands = listOf(
                    EqBand(120.0, 2.5, 0.7),
                    EqBand(1_000.0, -0.5, 1.1),
                    EqBand(8_000.0, 2.0, 0.75),
                ),
                compressor = CompressorSettings(
                    thresholdDb = -22.0,
                    ratio = 2.0,
                    attackMs = 28.0,
                    releaseMs = 240.0,
                    makeupGainDb = 1.5,
                ),
                limiter = LimiterSettings(ceilingDb = -1.0),
                noiseGate = NoiseGateSettings(
                    thresholdDb = -60.0,
                    attackMs = 8.0,
                    releaseMs = 260.0,
                ),
            )
        }
        val issues = processed.validationIssues()
        require(issues.isEmpty()) { issues.first() }
        return processed
    }
}
