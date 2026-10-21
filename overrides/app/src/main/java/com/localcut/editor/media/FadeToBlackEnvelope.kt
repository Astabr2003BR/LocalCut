package com.localcut.editor.media

/**
 * Deterministic brightness envelope for a clip participating in a fade-to-black transition.
 * Values are normalized to 0f = black and 1f = original brightness.
 */
data class FadeToBlackEnvelope(
    val durationUs: Long,
    val fadeInUs: Long,
    val fadeOutUs: Long,
) {
    init {
        require(durationUs > 0L) { "durationUs must be positive" }
        require(fadeInUs >= 0L) { "fadeInUs must be non-negative" }
        require(fadeOutUs >= 0L) { "fadeOutUs must be non-negative" }
        require(fadeInUs <= durationUs - fadeOutUs) {
            "fade windows must not overlap or exceed clip duration"
        }
    }

    fun brightnessAt(presentationTimeUs: Long): Float {
        if (presentationTimeUs < 0L || presentationTimeUs > durationUs) return 0f

        val fadeInBrightness = if (fadeInUs == 0L) {
            1f
        } else {
            (presentationTimeUs.toDouble() / fadeInUs.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

        val fadeOutBrightness = if (fadeOutUs == 0L) {
            1f
        } else {
            ((durationUs - presentationTimeUs).toDouble() / fadeOutUs.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        }

        return minOf(fadeInBrightness, fadeOutBrightness)
    }
}
