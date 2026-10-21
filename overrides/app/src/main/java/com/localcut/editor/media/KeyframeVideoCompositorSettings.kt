package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings

@OptIn(UnstableApi::class)
class KeyframeVideoCompositorSettings(
    private val visualInputLayers: List<Media3SequenceBlueprint>,
) : VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Size>): Size =
        VideoCompositorSettings.DEFAULT.getOutputSize(inputSizes)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val sequence = visualInputLayers.getOrNull(inputId)
            ?: return StaticOverlaySettings.Builder().build()
        return StaticOverlaySettings.Builder()
            .setAlphaScale(Media3SequenceOpacityResolver.alphaAt(sequence, presentationTimeUs))
            .build()
    }
}
