package com.localcut.editor.domain.model

data class EqBand(
    val frequencyHz: Double,
    val gainDb: Double,
    val q: Double = 1.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (!frequencyHz.isFinite() || frequencyHz !in 20.0..24_000.0) add("EQ frequency must be finite and in 20..24000 Hz")
        if (!gainDb.isFinite() || gainDb !in -24.0..24.0) add("EQ gain must be finite and in -24..24 dB")
        if (!q.isFinite() || q !in 0.1..20.0) add("EQ Q must be finite and in 0.1..20")
    }
}

data class CompressorSettings(
    val thresholdDb: Double = -18.0,
    val ratio: Double = 4.0,
    val attackMs: Double = 10.0,
    val releaseMs: Double = 120.0,
    val makeupGainDb: Double = 0.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (!thresholdDb.isFinite() || thresholdDb !in -80.0..0.0) add("compressor threshold must be in -80..0 dB")
        if (!ratio.isFinite() || ratio !in 1.0..100.0) add("compressor ratio must be in 1..100")
        if (!attackMs.isFinite() || attackMs !in 0.1..1_000.0) add("compressor attack must be in 0.1..1000 ms")
        if (!releaseMs.isFinite() || releaseMs !in 1.0..5_000.0) add("compressor release must be in 1..5000 ms")
        if (!makeupGainDb.isFinite() || makeupGainDb !in -24.0..24.0) add("compressor makeup gain must be in -24..24 dB")
    }
}

data class LimiterSettings(
    val ceilingDb: Double = -1.0,
    val releaseMs: Double = 80.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (!ceilingDb.isFinite() || ceilingDb !in -24.0..0.0) add("limiter ceiling must be in -24..0 dB")
        if (!releaseMs.isFinite() || releaseMs !in 1.0..5_000.0) add("limiter release must be in 1..5000 ms")
    }
}

data class NoiseGateSettings(
    val thresholdDb: Double = -50.0,
    val attackMs: Double = 5.0,
    val releaseMs: Double = 150.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (!thresholdDb.isFinite() || thresholdDb !in -100.0..0.0) add("noise gate threshold must be in -100..0 dB")
        if (!attackMs.isFinite() || attackMs !in 0.1..1_000.0) add("noise gate attack must be in 0.1..1000 ms")
        if (!releaseMs.isFinite() || releaseMs !in 1.0..5_000.0) add("noise gate release must be in 1..5000 ms")
    }
}

data class AudioProcessingSettings(
    val eqBands: List<EqBand> = emptyList(),
    val compressor: CompressorSettings? = null,
    val limiter: LimiterSettings? = null,
    val noiseGate: NoiseGateSettings? = null,
    val pan: Double = 0.0,
    val normalizationGainDb: Double = 0.0,
    val pitchSemitones: Double = 0.0,
) {
    fun validationIssues(): List<String> = buildList {
        if (eqBands.size > 16) add("audio EQ supports at most 16 bands")
        eqBands.forEach { addAll(it.validationIssues()) }
        compressor?.let { addAll(it.validationIssues()) }
        limiter?.let { addAll(it.validationIssues()) }
        noiseGate?.let { addAll(it.validationIssues()) }
        if (!pan.isFinite() || pan !in -1.0..1.0) add("audio pan must be finite and in -1..1")
        if (!normalizationGainDb.isFinite() || normalizationGainDb !in -48.0..48.0) add("normalization gain must be finite and in -48..48 dB")
        if (!pitchSemitones.isFinite() || pitchSemitones !in -24.0..24.0) add("pitch semitones must be finite and in -24..24")
    }

    fun hasSampleDsp(): Boolean =
        eqBands.isNotEmpty() || compressor != null || limiter != null || noiseGate != null ||
            pan != 0.0 || normalizationGainDb != 0.0
}

data class BeatMarker(
    val id: String,
    val timeUs: Long,
    val strength: Double = 1.0,
) {
    companion object {
        fun validationIssues(markers: List<BeatMarker>): List<String> = buildList {
            if (markers.map { it.id }.distinct().size != markers.size) add("beat marker ids must be unique")
            markers.forEach { marker ->
                if (marker.id.isBlank()) add("beat marker id must not be blank")
                if (marker.timeUs < 0L) add("beat marker time must be non-negative")
                if (!marker.strength.isFinite() || marker.strength !in 0.0..1.0) add("beat marker strength must be finite and in 0..1")
            }
            if (markers.zipWithNext().any { (a, b) -> a.timeUs >= b.timeUs }) add("beat markers must be strictly ordered")
        }
    }
}
