package com.localcut.editor.domain.animation

import com.localcut.editor.audio.WaveformData
import kotlin.math.max

object WaveformLipSyncCuePlanner {
    fun plan(
        waveform: WaveformData,
        actionDurationUs: Long,
    ): List<LipSyncCue> {
        require(actionDurationUs > 0L) { "action duration must be positive" }

        val peaks = waveform.peaks.map { peak ->
            if (peak.isFinite()) peak.toDouble().coerceIn(0.0, 1.0) else 0.0
        }
        if (peaks.isEmpty()) return emptyList()

        if (peaks.size == 1) {
            return if (peaks.single() >= MIN_MEANINGFUL_PEAK) {
                listOf(LipSyncCue(offsetUs = 0L, durationUs = actionDurationUs, openness = 1.0))
            } else {
                emptyList()
            }
        }

        val sorted = peaks.sorted()
        val baselineIndex = (((sorted.size - 1) * BASELINE_PERCENTILE).toInt())
            .coerceIn(0, sorted.lastIndex)
        val baseline = sorted[baselineIndex]
        val localPeak = sorted.last()
        val localRange = localPeak - baseline

        if (localPeak < MIN_MEANINGFUL_PEAK || localRange < MIN_LOCAL_RANGE) {
            return emptyList()
        }

        val noiseFloor = max(
            MIN_ABSOLUTE_NOISE_FLOOR,
            baseline + localRange * ACTIVE_RANGE_FRACTION,
        )
        val normalizationRange = localPeak - noiseFloor
        if (normalizationRange <= 0.0) return emptyList()

        data class ActiveBucket(val index: Int, val openness: Double)

        val active = peaks.mapIndexedNotNull { index, peak ->
            if (peak < noiseFloor) {
                null
            } else {
                ActiveBucket(
                    index = index,
                    openness = ((peak - noiseFloor) / normalizationRange).coerceIn(0.0, 1.0),
                )
            }
        }
        if (active.isEmpty()) return emptyList()

        val result = mutableListOf<LipSyncCue>()
        var runStartIndex = active.first().index
        var runEndIndex = active.first().index
        var runOpenness = active.first().openness

        fun flushRun() {
            val startUs = bucketBoundary(runStartIndex, peaks.size, actionDurationUs)
            val endUs = bucketBoundary(runEndIndex + 1, peaks.size, actionDurationUs)
            if (endUs > startUs) {
                result += LipSyncCue(
                    offsetUs = startUs,
                    durationUs = endUs - startUs,
                    openness = runOpenness.coerceIn(0.0, 1.0),
                )
            }
        }

        active.drop(1).forEach { bucket ->
            if (bucket.index == runEndIndex + 1) {
                runEndIndex = bucket.index
                runOpenness = max(runOpenness, bucket.openness)
            } else {
                flushRun()
                runStartIndex = bucket.index
                runEndIndex = bucket.index
                runOpenness = bucket.openness
            }
        }
        flushRun()

        return result
    }

    private fun bucketBoundary(index: Int, bucketCount: Int, durationUs: Long): Long =
        index.toLong() * durationUs / bucketCount.toLong()

    private const val BASELINE_PERCENTILE = 0.25
    private const val ACTIVE_RANGE_FRACTION = 0.15
    private const val MIN_ABSOLUTE_NOISE_FLOOR = 0.02
    private const val MIN_MEANINGFUL_PEAK = 0.04
    private const val MIN_LOCAL_RANGE = 0.03
}
