package com.localcut.editor.audio

import kotlin.math.pow

object AudioPitchPolicy {
    const val MIN_SEMITONES = -24.0
    const val MAX_SEMITONES = 24.0

    fun factorForSemitones(semitones: Double): Float {
        require(semitones.isFinite() && semitones in MIN_SEMITONES..MAX_SEMITONES) {
            "pitch semitones must be finite and in -24..24"
        }
        return 2.0.pow(semitones / 12.0).toFloat()
    }
}
