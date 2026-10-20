package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextHorizontalAlignment
import com.localcut.editor.domain.model.TextStyle

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
    fun compile(project: Project): List<TextOverlayRenderSpec> =
        project.textOverlays
            .sortedWith(compareBy<com.localcut.editor.domain.model.TextOverlay> { it.zIndex }.thenBy { it.id })
            .map { overlay ->
                val halfWidth = project.width / 2.0
                val halfHeight = project.height / 2.0
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
                    textScale = ((overlay.style.fontSizePx / BASE_TEXT_SIZE_PX) * overlay.transform.scale).toFloat(),
                    rotationDegrees = overlay.transform.rotationDeg.toFloat(),
                    opacity = overlay.opacity.toFloat(),
                    zIndex = overlay.zIndex,
                )
            }

    private const val BASE_TEXT_SIZE_PX = 100.0
}
