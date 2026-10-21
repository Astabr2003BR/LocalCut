package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextHorizontalAlignment
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import kotlin.math.min

data class TextOverlayRenderSpec(
    val id: String,
    val text: String,
    val startUs: Long,
    val endUs: Long,
    val style: TextStyle,
    val alignment: TextHorizontalAlignment,
    val backgroundAnchorX: Float,
    val backgroundAnchorY: Float,
    val textScale: Float,
    val rotationDegrees: Float,
    val opacity: Float,
    val zIndex: Int,
) {
    fun isActiveAt(presentationTimeUs: Long): Boolean =
        presentationTimeUs >= startUs && presentationTimeUs < endUs

    fun alphaAt(presentationTimeUs: Long): Float =
        if (isActiveAt(presentationTimeUs)) opacity else 0f
}

object TextOverlayRenderSpecCompiler {
    fun compile(
        project: Project,
        outputWidth: Int = project.width,
        outputHeight: Int = project.height,
    ): List<TextOverlayRenderSpec> {
        require(project.width > 0 && project.height > 0) { "project dimensions must be > 0" }
        require(outputWidth > 0 && outputHeight > 0) { "output dimensions must be > 0" }

        val outputScale = min(
            outputWidth.toDouble() / project.width.toDouble(),
            outputHeight.toDouble() / project.height.toDouble(),
        )
        val halfWidth = project.width / 2.0
        val halfHeight = project.height / 2.0

        return project.textOverlays
            .sortedWith(compareBy<TextOverlay> { it.zIndex }.thenBy { it.id })
            .map { overlay ->
                val anchorX = (overlay.transform.x / halfWidth).toFloat().coerceIn(-1f, 1f)
                val anchorY = (-overlay.transform.y / halfHeight).toFloat().coerceIn(-1f, 1f)
                TextOverlayRenderSpec(
                    id = overlay.id,
                    text = overlay.text,
                    startUs = overlay.startUs,
                    endUs = Math.addExact(overlay.startUs, overlay.durationUs),
                    style = overlay.style,
                    alignment = overlay.style.alignment,
                    backgroundAnchorX = anchorX,
                    backgroundAnchorY = anchorY,
                    textScale = (
                        (overlay.style.fontSizePx / BASE_TEXT_SIZE_PX) *
                            overlay.transform.scale *
                            outputScale
                    ).toFloat(),
                    rotationDegrees = overlay.transform.rotationDeg.toFloat(),
                    opacity = overlay.opacity.toFloat(),
                    zIndex = overlay.zIndex,
                )
            }
    }

    private const val BASE_TEXT_SIZE_PX = 100.0
}
