package com.localcut.editor.media

import com.localcut.editor.domain.model.AudioDuckingSettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Track
import kotlin.math.min
import kotlin.math.pow

data class AudioDuckingWindow(
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val attackUs: Long,
    val releaseUs: Long,
    val reductionDb: Double,
) {
    init {
        require(sourceEndUs > sourceStartUs) { "ducking source window must have positive duration" }
        require(attackUs >= 0L) { "ducking attack must be non-negative" }
        require(releaseUs >= 0L) { "ducking release must be non-negative" }
        require(reductionDb.isFinite() && reductionDb in -48.0..0.0) { "invalid ducking reduction" }
    }

    private val reducedGain: Double = 10.0.pow(reductionDb / 20.0)

    fun gainAtUs(positionUs: Long): Double {
        if (positionUs < sourceStartUs) return 1.0

        val attackEndUs = saturatingAdd(sourceStartUs, attackUs)
        if (positionUs < sourceEndUs) {
            if (attackUs == 0L || positionUs >= attackEndUs) return reducedGain
            val progress = ((positionUs - sourceStartUs).toDouble() / attackUs.toDouble()).coerceIn(0.0, 1.0)
            return 1.0 + (reducedGain - 1.0) * progress
        }

        if (releaseUs == 0L) return 1.0
        val releaseEndUs = saturatingAdd(sourceEndUs, releaseUs)
        if (positionUs >= releaseEndUs) return 1.0

        val gainAtReleaseStart = if (attackUs == 0L || sourceEndUs >= attackEndUs) {
            reducedGain
        } else {
            val attackProgress = ((sourceEndUs - sourceStartUs).toDouble() / attackUs.toDouble()).coerceIn(0.0, 1.0)
            1.0 + (reducedGain - 1.0) * attackProgress
        }
        val releaseProgress = ((positionUs - sourceEndUs).toDouble() / releaseUs.toDouble()).coerceIn(0.0, 1.0)
        return gainAtReleaseStart + (1.0 - gainAtReleaseStart) * releaseProgress
    }

    fun effectEndUs(): Long = saturatingAdd(sourceEndUs, releaseUs)

    private fun saturatingAdd(a: Long, b: Long): Long =
        try {
            Math.addExact(a, b)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
}

data class AudioDuckingEnvelope(
    val windows: List<AudioDuckingWindow> = emptyList(),
) {
    fun gainAtUs(positionUs: Long): Float {
        require(positionUs >= 0L) { "ducking gain position must be non-negative" }
        var gain = 1.0
        windows.forEach { window -> gain = min(gain, window.gainAtUs(positionUs)) }
        return gain.coerceIn(0.0, 1.0).toFloat()
    }

    fun hasEffectAtOrAfter(positionUs: Long): Boolean =
        windows.any { it.effectEndUs() > positionUs }

    fun nextWindowStartAtOrAfter(positionUs: Long): Long? =
        windows.asSequence()
            .map { it.sourceStartUs.coerceAtLeast(0L) }
            .filter { it >= positionUs }
            .minOrNull()
}

class AudioDuckingIndex(
    sourceTrack: Track,
    private val settings: AudioDuckingSettings,
) {
    private data class SourceInterval(
        val startUs: Long,
        val endUs: Long,
        val effectEndUs: Long,
    )

    private val intervals: List<SourceInterval>

    init {
        require(settings.sourceTrackId == sourceTrack.id) { "ducking source track does not match settings" }
        val issues = settings.validationIssues()
        require(issues.isEmpty()) { issues.first() }
        intervals = sourceTrack.clips
            .map { clip ->
                val endUs = saturatingAdd(clip.startUs, clip.durationUs)
                SourceInterval(
                    startUs = clip.startUs,
                    endUs = endUs,
                    effectEndUs = saturatingAdd(endUs, settings.releaseUs),
                )
            }
            .sortedBy { it.startUs }
    }

    fun plan(targetClip: Clip): AudioDuckingEnvelope {
        if (intervals.isEmpty()) return AudioDuckingEnvelope()
        val targetStartUs = targetClip.startUs
        val targetEndUs = saturatingAdd(targetStartUs, targetClip.durationUs)
        val firstCandidate = firstEffectEndingAfter(targetStartUs)
        if (firstCandidate >= intervals.size) return AudioDuckingEnvelope()

        val windows = mutableListOf<AudioDuckingWindow>()
        var index = firstCandidate
        while (index < intervals.size) {
            val interval = intervals[index]
            if (interval.startUs >= targetEndUs) break
            if (interval.effectEndUs > targetStartUs) {
                windows += AudioDuckingWindow(
                    sourceStartUs = safeDifference(interval.startUs, targetStartUs),
                    sourceEndUs = safeDifference(interval.endUs, targetStartUs),
                    attackUs = settings.attackUs,
                    releaseUs = settings.releaseUs,
                    reductionDb = settings.reductionDb,
                )
            }
            index++
        }
        return AudioDuckingEnvelope(windows)
    }

    private fun firstEffectEndingAfter(targetStartUs: Long): Int {
        var low = 0
        var high = intervals.size
        while (low < high) {
            val mid = low + (high - low) / 2
            if (intervals[mid].effectEndUs > targetStartUs) {
                high = mid
            } else {
                low = mid + 1
            }
        }
        return low
    }
}

object AudioDuckingPlanner {
    fun plan(
        targetClip: Clip,
        sourceTrack: Track,
        settings: AudioDuckingSettings,
    ): AudioDuckingEnvelope = AudioDuckingIndex(sourceTrack, settings).plan(targetClip)
}

private fun saturatingAdd(a: Long, b: Long): Long =
    try {
        Math.addExact(a, b)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }

private fun safeDifference(a: Long, b: Long): Long =
    try {
        Math.subtractExact(a, b)
    } catch (_: ArithmeticException) {
        if (a >= b) Long.MAX_VALUE else Long.MIN_VALUE
    }
