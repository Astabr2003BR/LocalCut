package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
internal class DuckingGainProvider(
    private val envelope: AudioDuckingEnvelope,
) : GainProcessor.GainProvider {
    override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float {
        require(samplePosition >= 0L) { "sample position must be non-negative" }
        require(sampleRate > 0) { "sample rate must be positive" }
        return envelope.gainAtUs(samplesToUs(samplePosition, sampleRate))
    }

    override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long {
        require(samplePosition >= 0L) { "sample position must be non-negative" }
        require(sampleRate > 0) { "sample rate must be positive" }
        val positionUs = samplesToUs(samplePosition, sampleRate)
        if (envelope.gainAtUs(positionUs) < 0.999999f) return C.TIME_UNSET
        if (!envelope.hasEffectAtOrAfter(positionUs)) return C.TIME_END_OF_SOURCE

        val nextStartUs = envelope.nextWindowStartAtOrAfter(positionUs)
            ?: return samplePosition + 1L
        val nextStartSample = usToSamplesFloor(nextStartUs, sampleRate)
        return if (nextStartSample > samplePosition) nextStartSample else samplePosition + 1L
    }

    private fun samplesToUs(samplePosition: Long, sampleRate: Int): Long {
        val wholeSeconds = samplePosition / sampleRate.toLong()
        val remainder = samplePosition % sampleRate.toLong()
        val wholeUs = saturatingMultiply(wholeSeconds, MICROS_PER_SECOND)
        val remainderUs = remainder * MICROS_PER_SECOND / sampleRate.toLong()
        return saturatingAdd(wholeUs, remainderUs)
    }

    private fun usToSamplesFloor(positionUs: Long, sampleRate: Int): Long {
        if (positionUs <= 0L) return 0L
        val wholeSeconds = positionUs / MICROS_PER_SECOND
        val remainderUs = positionUs % MICROS_PER_SECOND
        val wholeSamples = saturatingMultiply(wholeSeconds, sampleRate.toLong())
        val remainderSamples = remainderUs * sampleRate.toLong() / MICROS_PER_SECOND
        return saturatingAdd(wholeSamples, remainderSamples)
    }

    private fun saturatingMultiply(a: Long, b: Long): Long =
        try {
            Math.multiplyExact(a, b)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }

    private fun saturatingAdd(a: Long, b: Long): Long =
        try {
            Math.addExact(a, b)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
