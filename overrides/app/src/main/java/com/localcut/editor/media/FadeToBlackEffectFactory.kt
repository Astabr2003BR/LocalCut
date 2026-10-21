package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix

@OptIn(UnstableApi::class)
object FadeToBlackEffectFactory {
    fun create(
        durationUs: Long,
        spec: ClipTransitionRenderSpec?,
        presentationStartUs: Long = 0L,
    ): RgbMatrix? {
        if (spec == null || (spec.fadeInUs == 0L && spec.fadeOutUs == 0L)) return null
        require(presentationStartUs >= 0L) { "presentationStartUs must be non-negative" }

        val envelope = FadeToBlackEnvelope(
            durationUs = durationUs,
            fadeInUs = spec.fadeInUs,
            fadeOutUs = spec.fadeOutUs,
        )

        return object : RgbMatrix {
            override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
                val localTimeUs = presentationTimeUs - presentationStartUs
                val brightness = envelope.brightnessAt(localTimeUs)
                return floatArrayOf(
                    brightness, 0f, 0f, 0f,
                    0f, brightness, 0f, 0f,
                    0f, 0f, brightness, 0f,
                    0f, 0f, 0f, 1f,
                )
            }
        }
    }
}
