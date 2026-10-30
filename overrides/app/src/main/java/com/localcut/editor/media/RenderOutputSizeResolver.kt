package com.localcut.editor.media

import kotlin.math.roundToInt

data class RenderOutputSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "RENDER_OUTPUT_SIZE_INVALID" }
    }
}

object RenderOutputSizeResolver {
    const val DEFAULT_FAST_PREVIEW_LONG_EDGE = 720

    fun resolve(
        projectWidth: Int,
        projectHeight: Int,
        mode: RenderMode,
        outputSettings: RenderOutputSettings = RenderOutputSettings(),
    ): RenderOutputSize {
        require(projectWidth > 0 && projectHeight > 0) { "RENDER_PROJECT_SIZE_INVALID" }
        val width = outputSettings.width ?: projectWidth
        val height = outputSettings.height ?: projectHeight
        if (mode != RenderMode.FAST_PREVIEW) return RenderOutputSize(width, height)

        val previewLongEdge = outputSettings.previewMaxLongEdge ?: DEFAULT_FAST_PREVIEW_LONG_EDGE
        val longEdge = maxOf(width, height)
        if (longEdge <= previewLongEdge) return RenderOutputSize(width, height)
        val scale = previewLongEdge.toDouble() / longEdge.toDouble()
        return RenderOutputSize(
            width = maxOf(1, (width * scale).roundToInt()),
            height = maxOf(1, (height * scale).roundToInt()),
        )
    }
}
