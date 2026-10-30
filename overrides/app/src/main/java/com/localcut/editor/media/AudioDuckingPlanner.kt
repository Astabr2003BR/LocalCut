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

object AudioDuckingPlanner {
    fun plan(
        targetClip: Clip,
        sourceTrack: Track,
        settings: AudioDuckingSettings,
    ): AudioDuckingEnvelope {
        require(settings.sourceTrackId == sourceTrack.id) { "ducking source track does not match settings" }
        require(settings.validationIssues().isEmpty()) { settings.validationIssues().first() }

        val targetStartUs = targetClip.startUs
        val targetEndUs = saturatingAdd(targetStartUs, targetClip.durationUs)
        val windows = sourceTrack.clips.mapNotNull { sourceClip ->
            val sourceEndUs = saturatingAdd(sourceClip.startUs, sourceClip.durationUs)
            val effectEndUs = saturatingAdd(sourceEndUs, settings.releaseUs)
            val affectsTarget = sourceClip.startUs < targetEndUs && effectEndUs > targetStartUs
            if (!affectsTarget) return@mapNotNull null

            AudioDuckingWindow(
                sourceStartUs = safeDifference(sourceClip.startUs, targetStartUs),
                sourceEndUs = safeDifference(sourceEndUs, targetStartUs),
                attackUs = settings.attackUs,
                releaseUs = settings.releaseUs,
                reductionDb = settings.reductionDb,
            )
        }
        return AudioDuckingEnvelope(windows.sortedBy { it.sourceStartUs })
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
}
