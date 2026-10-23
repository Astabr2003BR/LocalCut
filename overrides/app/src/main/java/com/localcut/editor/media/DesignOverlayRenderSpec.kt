package com.localcut.editor.media

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeSpec
import kotlin.math.min

/** Render-ready immutable description of one timed vector/design shape. */
data class DesignOverlayRenderSpec(
    val id: String,
    val startUs: Long,
    val endUs: Long,
    val shape: ShapeSpec,
    val centerX: Float,
    val centerY: Float,
    val widthPx: Float,
    val heightPx: Float,
    val rotationDegrees: Float,
    val alpha: Float,
    val cornerRadiusPx: Float,
    val strokeWidthPx: Float,
    val zIndex: Int,
) {
    fun isActiveAt(presentationTimeUs: Long): Boolean =
        presentationTimeUs >= startUs && presentationTimeUs < endUs
}

object DesignOverlayRenderSpecCompiler {
    private const val BASE_SIZE_FRACTION_OF_SHORT_EDGE = 0.25

    fun compile(
        project: Project,
        outputWidth: Int = project.width,
        outputHeight: Int = project.height,
    ): List<DesignOverlayRenderSpec> {
        require(project.width > 0 && project.height > 0) { "project dimensions must be > 0" }
        require(outputWidth > 0 && outputHeight > 0) { "output dimensions must be > 0" }

        val outputScale = min(
            outputWidth.toDouble() / project.width.toDouble(),
            outputHeight.toDouble() / project.height.toDouble(),
        )
        val baseProjectSize = min(project.width, project.height) * BASE_SIZE_FRACTION_OF_SHORT_EDGE

        return project.designElements
            .asSequence()
            .filter { it.shape != null }
            .sortedWith(compareBy<DesignElement> { it.zIndex }.thenBy { it.id })
            .map { element ->
                val shape = requireNotNull(element.shape)
                val scale = element.transform.scale
                DesignOverlayRenderSpec(
                    id = element.id,
                    startUs = element.startUs,
                    endUs = Math.addExact(element.startUs, element.durationUs),
                    shape = shape,
                    centerX = (outputWidth / 2.0 + element.transform.x * outputScale).toFloat(),
                    centerY = (outputHeight / 2.0 + element.transform.y * outputScale).toFloat(),
                    widthPx = (baseProjectSize * scale * outputScale).toFloat(),
                    heightPx = (baseProjectSize * scale * outputScale).toFloat(),
                    rotationDegrees = element.transform.rotationDeg.toFloat(),
                    alpha = (element.opacity * element.transform.opacity).coerceIn(0.0, 1.0).toFloat(),
                    cornerRadiusPx = (shape.cornerRadiusPx * scale * outputScale).toFloat(),
                    strokeWidthPx = (shape.strokeWidthPx * scale * outputScale).toFloat(),
                    zIndex = element.zIndex,
                )
            }
            .toList()
    }
}
