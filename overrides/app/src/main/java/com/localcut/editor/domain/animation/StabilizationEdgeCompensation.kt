package com.localcut.editor.domain.animation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object StabilizationEdgeCompensation {
    fun requiredCoverageScale(
        canvasWidth: Int,
        canvasHeight: Int,
        translationX: Double,
        translationY: Double,
        rotationDeg: Double,
    ): Double {
        require(canvasWidth > 0 && canvasHeight > 0) { "stabilization canvas must be positive" }
        require(translationX.isFinite() && translationY.isFinite() && rotationDeg.isFinite()) {
            "stabilization edge correction must be finite"
        }

        val halfWidth = canvasWidth / 2.0
        val halfHeight = canvasHeight / 2.0
        val radians = rotationDeg * PI / 180.0
        val c = cos(radians)
        val s = sin(radians)

        // Transform canvas corners back into the corrected frame. The maximum
        // absolute local X/Y over all four corners has this closed form.
        val translatedLocalX = c * translationX + s * translationY
        val translatedLocalY = -s * translationX + c * translationY
        val maxLocalX = abs(c) * halfWidth + abs(s) * halfHeight + abs(translatedLocalX)
        val maxLocalY = abs(s) * halfWidth + abs(c) * halfHeight + abs(translatedLocalY)

        val scaleX = maxLocalX / halfWidth
        val scaleY = maxLocalY / halfHeight
        val required = max(1.0, max(scaleX, scaleY))
        require(required.isFinite()) { "stabilization edge scale invalid" }
        return required
    }
}
