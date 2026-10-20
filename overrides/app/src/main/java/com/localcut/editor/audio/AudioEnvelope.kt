package com.localcut.editor.audio

class AudioEnvelope(
    private val durationUs: Long,
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
) {
    init {
        require(durationUs > 0L) { "durationUs must be positive" }
        require(fadeInUs >= 0L) { "fadeInUs must not be negative" }
        require(fadeOutUs >= 0L) { "fadeOutUs must not be negative" }
        require(fadeInUs <= durationUs) { "fadeInUs exceeds clip duration" }
        require(fadeOutUs <= durationUs) { "fadeOutUs exceeds clip duration" }
        require(fadeInUs <= durationUs - fadeOutUs) { "audio fades must not overlap" }
    }

    fun gainFactorAt(offsetUs: Long): Double {
        val positionUs = offsetUs.coerceIn(0L, durationUs)

        if (fadeInUs > 0L && positionUs < fadeInUs) {
            return positionUs.toDouble() / fadeInUs.toDouble()
        }

        val fadeOutStartUs = durationUs - fadeOutUs
        if (fadeOutUs > 0L && positionUs > fadeOutStartUs) {
            return (durationUs - positionUs).toDouble() / fadeOutUs.toDouble()
        }

        return 1.0
    }
}
