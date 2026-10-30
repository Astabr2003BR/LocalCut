package com.localcut.editor.media

import kotlin.math.min

object MotionBlurPolicy {
    const val MAX_REQUESTED_SAMPLES = 64
    const val MAX_EFFECTIVE_SAMPLES = 8
    const val HISTORY_MEMORY_BUDGET_BYTES = 80L * 1024L * 1024L

    fun effectiveSamples(
        requestedSamples: Int,
        width: Int,
        height: Int,
        bytesPerPixel: Int,
    ): Int {
        require(requestedSamples in 1..MAX_REQUESTED_SAMPLES) {
            "motion blur requested samples must be in 1..$MAX_REQUESTED_SAMPLES"
        }
        require(width > 0 && height > 0) { "motion blur dimensions must be positive" }
        require(bytesPerPixel > 0) { "motion blur bytesPerPixel must be positive" }

        val frameBytes = Math.multiplyExact(
            Math.multiplyExact(width.toLong(), height.toLong()),
            bytesPerPixel.toLong(),
        )
        val historyCapacity = (HISTORY_MEMORY_BUDGET_BYTES / frameBytes)
            .coerceAtLeast(0L)
            .coerceAtMost((MAX_EFFECTIVE_SAMPLES - 1).toLong())
        val memoryBounded = (historyCapacity + 1L).toInt()
        return min(requestedSamples, min(MAX_EFFECTIVE_SAMPLES, memoryBounded)).coerceAtLeast(1)
    }

    fun weights(
        shutterAngle: Double,
        availableSamples: Int,
    ): List<Float> {
        require(shutterAngle.isFinite() && shutterAngle in 0.0..360.0) {
            "motion blur shutter angle must be finite and in 0..360"
        }
        require(availableSamples in 1..MAX_EFFECTIVE_SAMPLES) {
            "motion blur available samples must be in 1..$MAX_EFFECTIVE_SAMPLES"
        }
        if (availableSamples == 1) return listOf(1f)

        val shutter = shutterAngle / 360.0
        val denominator = (availableSamples - 1).toDouble()
        val raw = MutableList(availableSamples) { index ->
            if (index == 0) {
                1.0
            } else {
                shutter * (availableSamples - index).toDouble() / denominator
            }
        }
        val total = raw.sum()
        require(total.isFinite() && total > 0.0) { "motion blur weights must have positive finite sum" }
        return raw.map { (it / total).toFloat() }
    }
}
