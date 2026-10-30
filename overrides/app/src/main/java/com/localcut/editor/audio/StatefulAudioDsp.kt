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

/**
 * Stateful counterpart of [AudioDsp] for streaming/chunked audio processing.
 *
 * EQ filter history, noise-gate gain and compressor gain are preserved across [process] calls so
 * splitting one continuous stream into arbitrary buffers produces the same output as processing
 * the same samples in a single buffer. [reset] restores the deterministic initial state.
 */
class StatefulAudioDsp(
    private val channelCount: Int,
    private val sampleRateHz: Int,
    private val settings: AudioProcessingSettings,
) {
    init {
        require(channelCount > 0) { "channelCount must be positive" }
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(settings.validationIssues().isEmpty()) {
            "invalid audio processing: ${settings.validationIssues().first()}"
        }
    }

    private val normalizationGain = dbToLinear(settings.normalizationGainDb)
    private val eqStates = settings.eqBands.map { band -> PeakingEqState(band) }
    private val noiseGateState = settings.noiseGate?.let { gate -> NoiseGateState(gate) }
    private val compressorState = settings.compressor?.let { compressor -> CompressorState(compressor) }
    private val limiterCeiling = settings.limiter?.let { limiter -> dbToLinear(limiter.ceilingDb).toFloat() }

    fun process(samples: FloatArray): FloatArray {
        require(samples.size % channelCount == 0) {
            "interleaved sample count must align to channel count"
        }
        if (samples.isEmpty()) return samples.copyOf()

        val out = samples.copyOf()
        applyGain(out, normalizationGain)
        eqStates.forEach { state -> state.process(out) }
        noiseGateState?.process(out)
        compressorState?.process(out)
        applyPan(out, settings.pan)
        limiterCeiling?.let { ceiling ->
            for (index in out.indices) {
                out[index] = out[index].coerceIn(-ceiling, ceiling)
            }
        }
        return out
    }

    fun reset() {
        eqStates.forEach { state -> state.reset() }
        noiseGateState?.reset()
        compressorState?.reset()
    }

    private inner class PeakingEqState(band: EqBand) {
        private val enabled = band.gainDb != 0.0
        private val w0 = 2.0 * PI * band.frequencyHz / sampleRateHz.toDouble()
        private val alpha = sin(w0) / (2.0 * band.q)
        private val a = 10.0.pow(band.gainDb / 40.0)
        private val a0 = 1.0 + alpha / a
        private val nb0 = (1.0 + alpha * a) / a0
        private val nb1 = (-2.0 * cos(w0)) / a0
        private val nb2 = (1.0 - alpha * a) / a0
        private val na1 = (-2.0 * cos(w0)) / a0
        private val na2 = (1.0 - alpha / a) / a0
        private val x1 = DoubleArray(channelCount)
        private val x2 = DoubleArray(channelCount)
        private val y1 = DoubleArray(channelCount)
        private val y2 = DoubleArray(channelCount)

        fun process(samples: FloatArray) {
            if (!enabled) return
            var index = 0
            while (index < samples.size) {
                for (channel in 0 until channelCount) {
                    val sampleIndex = index + channel
                    val x = samples[sampleIndex].toDouble()
                    val y = nb0 * x + nb1 * x1[channel] + nb2 * x2[channel] -
                        na1 * y1[channel] - na2 * y2[channel]
                    x2[channel] = x1[channel]
                    x1[channel] = x
                    y2[channel] = y1[channel]
                    y1[channel] = y
                    samples[sampleIndex] = y.coerceIn(-4.0, 4.0).toFloat()
                }
                index += channelCount
            }
        }

        fun reset() {
            x1.fill(0.0)
            x2.fill(0.0)
            y1.fill(0.0)
            y2.fill(0.0)
        }
    }

    private inner class NoiseGateState(private val gate: NoiseGateSettings) {
        private val threshold = dbToLinear(gate.thresholdDb)
        private val attackCoeff = smoothingCoefficient(gate.attackMs, sampleRateHz)
        private val releaseCoeff = smoothingCoefficient(gate.releaseMs, sampleRateHz)
        private var gain = 1.0

        fun process(samples: FloatArray) {
            var frame = 0
            while (frame * channelCount < samples.size) {
                val base = frame * channelCount
                var peak = 0.0
                for (channel in 0 until channelCount) {
                    peak = maxOf(peak, abs(samples[base + channel].toDouble()))
                }
                val target = if (peak >= threshold) 1.0 else 0.0
                val coeff = if (target > gain) attackCoeff else releaseCoeff
                gain = target + coeff * (gain - target)
                for (channel in 0 until channelCount) {
                    samples[base + channel] = (samples[base + channel] * gain).toFloat()
                }
                frame++
            }
        }

        fun reset() {
            gain = 1.0
        }
    }

    private inner class CompressorState(private val compressor: CompressorSettings) {
        private val attackCoeff = smoothingCoefficient(compressor.attackMs, sampleRateHz)
        private val releaseCoeff = smoothingCoefficient(compressor.releaseMs, sampleRateHz)
        private var gain = 1.0

        fun process(samples: FloatArray) {
            var frame = 0
            while (frame * channelCount < samples.size) {
                val base = frame * channelCount
                var peak = 0.0
                for (channel in 0 until channelCount) {
                    peak = maxOf(peak, abs(samples[base + channel].toDouble()))
                }
                val inputDb = linearToDb(peak)
                val targetGainDb = if (inputDb > compressor.thresholdDb) {
                    val outputDb = compressor.thresholdDb +
                        (inputDb - compressor.thresholdDb) / compressor.ratio
                    outputDb - inputDb + compressor.makeupGainDb
                } else {
                    compressor.makeupGainDb
                }
                val targetGain = dbToLinear(targetGainDb)
                val coeff = if (targetGain < gain) attackCoeff else releaseCoeff
                gain = targetGain + coeff * (gain - targetGain)
                for (channel in 0 until channelCount) {
                    samples[base + channel] = (samples[base + channel] * gain).toFloat()
                }
                frame++
            }
        }

        fun reset() {
            gain = 1.0
        }
    }

    private fun applyGain(samples: FloatArray, gain: Double) {
        if (gain == 1.0) return
        for (index in samples.indices) {
            samples[index] = (samples[index] * gain).toFloat()
        }
    }

    private fun applyPan(samples: FloatArray, pan: Double) {
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

    private fun smoothingCoefficient(timeMs: Double, sampleRateHz: Int): Double =
        exp(-1.0 / ((timeMs / 1_000.0) * sampleRateHz.toDouble()).coerceAtLeast(1.0))

    private fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    private fun linearToDb(linear: Double): Double =
        if (linear <= 1e-12) -240.0 else 20.0 * log10(linear)
}
