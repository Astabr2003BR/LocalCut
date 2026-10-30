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

    fun setPitchSemitones(source: AudioProcessingSettings, semitones: Double): AudioProcessingSettings {
        require(semitones.isFinite() && semitones in -24.0..24.0) { "pitch semitones must be in -24..24" }
        return validated(source.copy(pitchSemitones = semitones))
    }

    fun setEqGain(
        source: AudioProcessingSettings,
        band: AudioEqBand,
        gainDb: Double,
    ): AudioProcessingSettings {
        require(gainDb.isFinite() && gainDb in -24.0..24.0) { "EQ gain must be in -24..24 dB" }
        return updateStandardEqBand(source, band) { it.copy(gainDb = gainDb) }
    }

    fun setEqQ(
        source: AudioProcessingSettings,
        band: AudioEqBand,
        q: Double,
    ): AudioProcessingSettings {
        require(q.isFinite() && q in 0.1..20.0) { "EQ Q must be in 0.1..20" }
        return updateStandardEqBand(source, band) { it.copy(q = q) }
    }

    fun eqGain(source: AudioProcessingSettings, band: AudioEqBand): Double =
        source.eqBands.firstOrNull { it.frequencyHz == band.frequencyHz }?.gainDb ?: 0.0

    fun eqQ(source: AudioProcessingSettings, band: AudioEqBand): Double =
        source.eqBands.firstOrNull { it.frequencyHz == band.frequencyHz }?.q ?: 1.0

    fun setCompressorEnabled(source: AudioProcessingSettings, enabled: Boolean): AudioProcessingSettings =
        validated(source.copy(compressor = if (enabled) source.compressor ?: CompressorSettings() else null))

    fun setLimiterEnabled(source: AudioProcessingSettings, enabled: Boolean): AudioProcessingSettings =
        validated(source.copy(limiter = if (enabled) source.limiter ?: LimiterSettings() else null))

    fun setNoiseGateEnabled(source: AudioProcessingSettings, enabled: Boolean): AudioProcessingSettings =
        validated(source.copy(noiseGate = if (enabled) source.noiseGate ?: NoiseGateSettings() else null))

    fun setCompressorThreshold(source: AudioProcessingSettings, thresholdDb: Double): AudioProcessingSettings {
        require(thresholdDb.isFinite() && thresholdDb in -80.0..0.0) { "compressor threshold must be in -80..0 dB" }
        return updateCompressor(source) { it.copy(thresholdDb = thresholdDb) }
    }

    fun setCompressorRatio(source: AudioProcessingSettings, ratio: Double): AudioProcessingSettings {
        require(ratio.isFinite() && ratio in 1.0..100.0) { "compressor ratio must be in 1..100" }
        return updateCompressor(source) { it.copy(ratio = ratio) }
    }

    fun setCompressorAttack(source: AudioProcessingSettings, attackMs: Double): AudioProcessingSettings {
        require(attackMs.isFinite() && attackMs in 0.1..1_000.0) { "compressor attack must be in 0.1..1000 ms" }
        return updateCompressor(source) { it.copy(attackMs = attackMs) }
    }

    fun setCompressorRelease(source: AudioProcessingSettings, releaseMs: Double): AudioProcessingSettings {
        require(releaseMs.isFinite() && releaseMs in 1.0..5_000.0) { "compressor release must be in 1..5000 ms" }
        return updateCompressor(source) { it.copy(releaseMs = releaseMs) }
    }

    fun setCompressorMakeupGain(source: AudioProcessingSettings, gainDb: Double): AudioProcessingSettings {
        require(gainDb.isFinite() && gainDb in -24.0..24.0) { "compressor makeup gain must be in -24..24 dB" }
        return updateCompressor(source) { it.copy(makeupGainDb = gainDb) }
    }

    fun setLimiterCeiling(source: AudioProcessingSettings, ceilingDb: Double): AudioProcessingSettings {
        require(ceilingDb.isFinite() && ceilingDb in -24.0..0.0) { "limiter ceiling must be in -24..0 dB" }
        val limiter = source.limiter ?: return source
        return validated(source.copy(limiter = limiter.copy(ceilingDb = ceilingDb)))
    }

    fun setNoiseGateThreshold(source: AudioProcessingSettings, thresholdDb: Double): AudioProcessingSettings {
        require(thresholdDb.isFinite() && thresholdDb in -100.0..0.0) { "noise gate threshold must be in -100..0 dB" }
        return updateNoiseGate(source) { it.copy(thresholdDb = thresholdDb) }
    }

    fun setNoiseGateAttack(source: AudioProcessingSettings, attackMs: Double): AudioProcessingSettings {
        require(attackMs.isFinite() && attackMs in 0.1..1_000.0) { "noise gate attack must be in 0.1..1000 ms" }
        return updateNoiseGate(source) { it.copy(attackMs = attackMs)) }
    }

    fun setNoiseGateRelease(source: AudioProcessingSettings, releaseMs: Double): AudioProcessingSettings {
        require(releaseMs.isFinite() && releaseMs in 1.0..5_000.0) { "noise gate release must be in 1..5000 ms" }
        return updateNoiseGate(source) { it.copy(releaseMs = releaseMs) }
    }

    fun reset(): AudioProcessingSettings = AudioProcessingSettings()

    private fun updateStandardEqBand(
        source: AudioProcessingSettings,
        band: AudioEqBand,
        transform: (EqBand) -> EqBand,
    ): AudioProcessingSettings {
        val byFrequency = source.eqBands.associateBy { it.frequencyHz }.toMutableMap()
        AudioEqBand.entries.forEach { definition ->
            byFrequency.putIfAbsent(definition.frequencyHz, EqBand(definition.frequencyHz, 0.0, q = 1.0))
        }
        byFrequency[band.frequencyHz] = transform(byFrequency.getValue(band.frequencyHz))
        val standard = AudioEqBand.entries.map { byFrequency.getValue(it.frequencyHz) }
        val custom = source.eqBands.filter { existing -> AudioEqBand.entries.none { it.frequencyHz == existing.frequencyHz } }
        return validated(source.copy(eqBands = standard + custom))
    }

    private fun updateCompressor(
        source: AudioProcessingSettings,
        transform: (CompressorSettings) -> CompressorSettings,
    ): AudioProcessingSettings {
        val compressor = source.compressor ?: return source
        return validated(source.copy(compressor = transform(compressor)))
    }

    private fun updateNoiseGate(
        source: AudioProcessingSettings,
        transform: (NoiseGateSettings) -> NoiseGateSettings,
    ): AudioProcessingSettings {
        val gate = source.noiseGate ?: return source
        return validated(source.copy(noiseGate = transform(gate)))
    }

    private fun validated(settings: AudioProcessingSettings): AudioProcessingSettings {
        val issues = settings.validationIssues()
        require(issues.isEmpty()) { issues.first() }
        return settings
    }
}
