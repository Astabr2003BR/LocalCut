package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.EqBand
import com.localcut.editor.domain.model.LimiterSettings
import com.localcut.editor.domain.model.NoiseGateSettings

enum class AudioEqBand(val frequencyHz: Double) {
    LOW(120.0),
    MID(1_000.0),
    HIGH(8_000.0),
}

object AudioToolsUiPolicy {
    fun setPan(source: AudioProcessingSettings, pan: Double): AudioProcessingSettings {
        require(pan.isFinite() && pan in -1.0..1.0) { "pan must be finite and in -1..1" }
        return validated(source.copy(pan = pan))
    }

    fun setNormalizationGain(source: AudioProcessingSettings, gainDb: Double): AudioProcessingSettings {
        require(gainDb.isFinite() && gainDb in -48.0..48.0) { "normalization gain must be in -48..48 dB" }
        return validated(source.copy(normalizationGainDb = gainDb))
    }

    fun setEqGain(
        source: AudioProcessingSettings,
        band: AudioEqBand,
        gainDb: Double,
    ): AudioProcessingSettings {
        require(gainDb.isFinite() && gainDb in -24.0..24.0) { "EQ gain must be in -24..24 dB" }
        val byFrequency = source.eqBands.associateBy { it.frequencyHz }.toMutableMap()
        AudioEqBand.entries.forEach { definition ->
            byFrequency.putIfAbsent(definition.frequencyHz, EqBand(definition.frequencyHz, 0.0, q = 1.0))
        }
        byFrequency[band.frequencyHz] = byFrequency.getValue(band.frequencyHz).copy(gainDb = gainDb)
        val standard = AudioEqBand.entries.map { byFrequency.getValue(it.frequencyHz) }
        val custom = source.eqBands.filter { existing -> AudioEqBand.entries.none { it.frequencyHz == existing.frequencyHz } }
        return validated(source.copy(eqBands = standard + custom))
    }

    fun eqGain(source: AudioProcessingSettings, band: AudioEqBand): Double =
        source.eqBands.firstOrNull { it.frequencyHz == band.frequencyHz }?.gainDb ?: 0.0

    fun setCompressorEnabled(source: AudioProcessingSettings, enabled: Boolean): AudioProcessingSettings =
        validated(source.copy(compressor = if (enabled) source.compressor ?: CompressorSettings() else null))

    fun setLimiterEnabled(source: AudioProcessingSettings, enabled: Boolean): AudioProcessingSettings =
        validated(source.copy(limiter = if (enabled) source.limiter ?: LimiterSettings() else null))

    fun setNoiseGateEnabled(source: AudioProcessingSettings, enabled: Boolean): AudioProcessingSettings =
        validated(source.copy(noiseGate = if (enabled) source.noiseGate ?: NoiseGateSettings() else null))

    fun reset(): AudioProcessingSettings = AudioProcessingSettings()

    private fun validated(settings: AudioProcessingSettings): AudioProcessingSettings {
        val issues = settings.validationIssues()
        require(issues.isEmpty()) { issues.first() }
        return settings
    }
}
