package com.localcut.editor.audio

import com.localcut.editor.domain.model.BeatMarker
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class BeatDetectionSettings(
    val analysisWindowMs: Int = 40,
    val minimumIntervalMs: Int = 240,
    val thresholdMultiplier: Double = 1.6,
) {
    fun validationIssues(): List<String> = buildList {
        if (analysisWindowMs !in 5..1_000) add("analysis window must be in 5..1000 ms")
        if (minimumIntervalMs !in 20..10_000) add("minimum beat interval must be in 20..10000 ms")
        if (!thresholdMultiplier.isFinite() || thresholdMultiplier !in 1.0..20.0) {
            add("beat threshold multiplier must be finite and in 1..20")
        }
    }
}

object BeatDetector {
    fun detect(
        monoSamples: FloatArray,
        sampleRateHz: Int,
        settings: BeatDetectionSettings = BeatDetectionSettings(),
    ): List<BeatMarker> {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        val issues = settings.validationIssues()
        require(issues.isEmpty()) { "invalid beat detection settings: ${issues.first()}" }
        if (monoSamples.isEmpty()) return emptyList()
        require(monoSamples.all { it.isFinite() }) { "audio samples must be finite" }

        val windowSamples = max(
            1,
            ((sampleRateHz.toLong() * settings.analysisWindowMs) / 1_000L).toInt(),
        )
        val envelopes = ArrayList<WindowEnergy>((monoSamples.size + windowSamples - 1) / windowSamples)
        var start = 0
        while (start < monoSamples.size) {
            val end = minOf(monoSamples.size, start + windowSamples)
            var sum = 0.0
            var peakAbs = -1.0f
            var peakIndex = start
            for (index in start until end) {
                val magnitude = abs(monoSamples[index])
                sum += magnitude.toDouble()
                if (magnitude > peakAbs) {
                    peakAbs = magnitude
                    peakIndex = index
                }
            }
            envelopes += WindowEnergy(
                energy = sum / (end - start).coerceAtLeast(1).toDouble(),
                peakSampleIndex = peakIndex,
            )
            start = end
        }

        val maxEnergy = envelopes.maxOfOrNull { it.energy } ?: 0.0
        if (maxEnergy <= 1e-12) return emptyList()
        val meanEnergy = envelopes.sumOf { it.energy } / envelopes.size.toDouble()
        val threshold = max(1e-12, meanEnergy * settings.thresholdMultiplier)
        val candidates = envelopes.mapIndexedNotNull { index, window ->
            val left = envelopes.getOrNull(index - 1)?.energy ?: Double.NEGATIVE_INFINITY
            val right = envelopes.getOrNull(index + 1)?.energy ?: Double.NEGATIVE_INFINITY
            if (window.energy >= threshold && window.energy >= left && window.energy >= right) {
                BeatCandidate(
                    sampleIndex = window.peakSampleIndex,
                    energy = window.energy,
                )
            } else {
                null
            }
        }

        if (candidates.isEmpty()) return emptyList()
        val minimumSamples = max(
            1L,
            sampleRateHz.toLong() * settings.minimumIntervalMs.toLong() / 1_000L,
        )
        val accepted = mutableListOf<BeatCandidate>()
        candidates.sortedBy { it.sampleIndex }.forEach { candidate ->
            val previous = accepted.lastOrNull()
            if (previous == null || candidate.sampleIndex.toLong() - previous.sampleIndex.toLong() >= minimumSamples) {
                accepted += candidate
            } else if (candidate.energy > previous.energy) {
                accepted[accepted.lastIndex] = candidate
            }
        }

        return accepted.mapIndexed { index, candidate ->
            val timeUs = candidate.sampleIndex.toLong() * 1_000_000L / sampleRateHz.toLong()
            BeatMarker(
                id = "beat-${index + 1}",
                timeUs = timeUs,
                strength = (candidate.energy / maxEnergy).coerceIn(0.0, 1.0),
            )
        }.also { markers ->
            val validation = BeatMarker.validationIssues(markers)
            require(validation.isEmpty()) { "invalid beat markers: ${validation.first()}" }
        }
    }

    private data class WindowEnergy(
        val energy: Double,
        val peakSampleIndex: Int,
    )

    private data class BeatCandidate(
        val sampleIndex: Int,
        val energy: Double,
    )
}
