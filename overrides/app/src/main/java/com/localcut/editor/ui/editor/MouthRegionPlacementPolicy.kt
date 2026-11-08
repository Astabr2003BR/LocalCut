package com.localcut.editor.ui.editor

import com.localcut.editor.domain.animation.MouthRegion
import kotlin.math.min

/** The exact displayed media rectangle for ContentScale.Fit, excluding letterbox areas. */
data class MouthImageFitRect(
    val leftPx: Double,
    val topPx: Double,
    val widthPx: Double,
    val heightPx: Double,
)

object MouthRegionPlacementPolicy {
    /**
     * Convert Compose Float slider values without losing inclusive Double limits
     * (e.g., 0.005f is slightly below 0.005 as Double).
     */
    fun fromUiValues(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float): MouthRegion {
        require(listOf(centerX, centerY, radiusX, radiusY).all(Float::isFinite)) {
            "mouth controls must be finite"
        }
        require(radiusX in 0.00999f..0.50001f && radiusY in 0.00499f..0.30001f) {
            "mouth radius outside slider bounds"
        }
        val rx = radiusX.toDouble().coerceIn(0.01, 0.50)
        val ry = radiusY.toDouble().coerceIn(0.005, 0.30)
        return MouthRegion(
            centerX.toDouble().coerceIn(rx, 1.0 - rx),
            centerY.toDouble().coerceIn(ry, 1.0 - ry),
            rx, ry,
        )
    }

    fun fit(
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        viewportWidthPx: Double,
        viewportHeightPx: Double,
    ): MouthImageFitRect {
        require(sourceWidthPx > 0 && sourceHeightPx > 0) { "source image dimensions must be positive" }
        require(viewportWidthPx.isFinite() && viewportHeightPx.isFinite() &&
            viewportWidthPx > 0.0 && viewportHeightPx > 0.0
        ) { "viewport must have finite positive dimensions" }
        val scale = min(
            viewportWidthPx / sourceWidthPx.toDouble(),
            viewportHeightPx / sourceHeightPx.toDouble(),
        )
        val width = sourceWidthPx * scale
        val height = sourceHeightPx * scale
        return MouthImageFitRect(
            leftPx = (viewportWidthPx - width) / 2.0,
            topPx = (viewportHeightPx - height) / 2.0,
            widthPx = width,
            heightPx = height,
        )
    }

    /** A tap in the black bars is ignored; inside the image it repositions the complete oval. */
    fun place(
        current: MouthRegion,
        touchX: Double,
        touchY: Double,
        fit: MouthImageFitRect,
    ): MouthRegion? {
        if (!touchX.isFinite() || !touchY.isFinite()) return null
        if (fit.widthPx <= 0.0 || fit.heightPx <= 0.0) return null
        if (touchX < fit.leftPx || touchX > fit.leftPx + fit.widthPx ||
            touchY < fit.topPx || touchY > fit.topPx + fit.heightPx
        ) return null
        validate(current)
        return current.copy(
            centerX = ((touchX - fit.leftPx) / fit.widthPx)
                .coerceIn(current.radiusX, 1.0 - current.radiusX),
            centerY = ((touchY - fit.topPx) / fit.heightPx)
                .coerceIn(current.radiusY, 1.0 - current.radiusY),
        )
    }

    fun withRadii(current: MouthRegion, radiusX: Double, radiusY: Double): MouthRegion {
        val updated = current.copy(radiusX = radiusX, radiusY = radiusY)
        validate(updated)
        return updated.copy(
            centerX = updated.centerX.coerceIn(radiusX, 1.0 - radiusX),
            centerY = updated.centerY.coerceIn(radiusY, 1.0 - radiusY),
        )
    }

    fun ellipseBounds(region: MouthRegion, fit: MouthImageFitRect): MouthImageFitRect {
        validate(region)
        require(fit.widthPx > 0.0 && fit.heightPx > 0.0) { "invalid fitted image rect" }
        return MouthImageFitRect(
            leftPx = fit.leftPx + (region.centerX - region.radiusX) * fit.widthPx,
            topPx = fit.topPx + (region.centerY - region.radiusY) * fit.heightPx,
            widthPx = region.radiusX * 2.0 * fit.widthPx,
            heightPx = region.radiusY * 2.0 * fit.heightPx,
        )
    }

    private fun validate(region: MouthRegion) {
        require(region.centerX.isFinite() && region.centerX in 0.0..1.0)
        require(region.centerY.isFinite() && region.centerY in 0.0..1.0)
        require(region.radiusX.isFinite() && region.radiusX in 0.01..0.50)
        require(region.radiusY.isFinite() && region.radiusY in 0.005..0.30)
    }
}
