package com.localcut.editor.audio

import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.EqBand
import com.localcut.editor.domain.model.NoiseGateSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

object AudioDsp {
    fun processInterleaved(
        samples: FloatArray,
        channelCount: Int,
        sampleRateHz: Int,
        settings: AudioProcessingSettings,
    ): FloatArray {
        require(channelCount > 0) { "channelCount must be positive" }
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(samples.size % channelCount == 0) { "interleaved sample count must align to channel count" }
        require(settings.validationIssues().isEmpty()) { "invalid audio processing: ${settings.validationIssues().first()}" }
        if (samples.isEmpty()) return samples.copyOf()

        val out = samples.copyOf()
        applyGain(out, dbToLinear(settings.normalizationGainDb))
        settings.eqBands.forEach { applyPeakingEq(out, channelCount, sampleRateHz, it) }
        settings.noiseGate?.let { applyNoiseGate(out, channelCount, sampleRateHz, it) }
        settings.compressor?.let { applyCompressor(out, channelCount, sampleRateHz, it) }
        applyPan(out, channelCount, settings.pan)
        settings.limiter?.let { limiter ->
            val ceiling = dbToLinear(limiter.ceilingDb).toFloat()
            for (index in out.indices) out[index] = out[index].coerceIn(-ceiling, ceiling)
        }
        return out
    }

    private fun applyGain(samples: FloatArray, gain: Double) {
        if (gain == 1.0) return
        for (index in samples.indices) samples[index] = (samples[index] * gain).toFloat()
    }

    private fun applyPan(samples: FloatArray, channelCount: Int, pan: Double) {
        if (channelCount != 2 || pan == 0.0) return
        val leftGain = if (pan > 0.0) 1.0 - pan else 1.0
        val rightGain = if (pan < 0.0) 1.0 + pan else 1.0
        var index = 0
        while (index < samples.size) {
            samples[index] = (samples[index] * leftGain).toFloat()
            samples[index + 1] = (samples[index + 1] * rightGain).toFloat()
            index += 2
        }
    }

    private fun applyPeakingEq(
        samples: FloatArray,
        channelCount: Int,
        sampleRateHz: Int,
        band: EqBand,
    ) {
        if (band.gainDb == 0.0) return
        val w0 = 2.0 * PI * band.frequencyHz / sampleRateHz.toDouble()
        val alpha = sin(w0) / (2.0 * band.q)
        val a = 10.0.pow(band.gainDb / 40.0)
        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cos(w0)
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cos(w0)
        val a2 = 1.0 - alpha / a
        val nb0 = b0 / a0
        val nb1 = b1 / a0
        val nb2 = b2 / a0
        val na1 = a1 / a0
        val na2 = a2 / a0
        val x1 = DoubleArray(channelCount)
        val x2 = DoubleArray(channelCount)
        val y1 = DoubleArray(channelCount)
        val y2 = DoubleArray(channelCount)

        var index = 0
        while (index < samples.size) {
            for (channel in 0 until channelCount) {
                val sampleIndex = index + channel
                val x = samples[sampleIndex].toDouble()
                val y = nb0 * x + nb1 * x1[channel] + nb2 * x2[channel] - na1 * y1[channel] - na2 * y2[channel]
                x2[channel] = x1[channel]
                x1[channel] = x
                y2[channel] = y1[channel]
                y1[channel] = y
                samples[sampleIndex] = y.coerceIn(-4.0, 4.0).toFloat()
            }
            index += channelCount
        }
    }

    private fun applyCompressor(
        samples: FloatArray,
        channelCount: Int,
        sampleRateHz: Int,
        settings: CompressorSettings,
    ) {
        val attackCoeff = smoothingCoefficient(settings.attackMs, sampleRateHz)
        val releaseCoeff = smoothingCoefficient(settings.releaseMs, sampleRateHz)
        var gain = 1.0
        var frame = 0
        while (frame * channelCount < samples.size) {
            val base = frame * channelCount
            var peak = 0.0
            for (channel in 0 until channelCount) peak = maxOf(peak, abs(samples[base + channel].toDouble()))
            val inputDb = linearToDb(peak)
            val targetGainDb = if (inputDb > settings.thresholdDb) {
                val outputDb = settings.thresholdDb + (inputDb - settings.thresholdDb) / settings.ratio
                outputDb - inputDb + settings.makeupGainDb
            } else {
                settings.makeupGainDb
            }
            val targetGain = dbToLinear(targetGainDb)
            val coeff = if (targetGain < gain) attackCoeff else releaseCoeff
            gain = targetGain + coeff * (gain - targetGain)
            for (channel in 0 until channelCount) samples[base + channel] = (samples[base + channel] * gain).toFloat()
            frame++
        }
    }

    private fun applyNoiseGate(
        samples: FloatArray,
        channelCount: Int,
        sampleRateHz: Int,
        settings: NoiseGateSettings,
    ) {
        val threshold = dbToLinear(settings.thresholdDb)
        val attackCoeff = smoothingCoefficient(settings.attackMs, sampleRateHz)
        val releaseCoeff = smoothingCoefficient(settings.releaseMs, sampleRateHz)
        var gateGain = 1.0
        var frame = 0
        while (frame * channelCount < samples.size) {
            val base = frame * channelCount
            var peak = 0.0
            for (channel in 0 until channelCount) peak = maxOf(peak, abs(samples[base + channel].toDouble()))
            val target = if (peak >= threshold) 1.0 else 0.0
            val coeff = if (target > gateGain) attackCoeff else releaseCoeff
            gateGain = target + coeff * (gateGain - target)
            for (channel in 0 until channelCount) samples[base + channel] = (samples[base + channel] * gateGain).toFloat()
            frame++
        }
    }

    private fun smoothingCoefficient(timeMs: Double, sampleRateHz: Int): Double =
        exp(-1.0 / ((timeMs / 1_000.0) * sampleRateHz.toDouble()).coerceAtLeast(1.0))

    private fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    private fun linearToDb(linear: Double): Double =
        if (linear <= 1e-12) -240.0 else 20.0 * log10(linear)
}
