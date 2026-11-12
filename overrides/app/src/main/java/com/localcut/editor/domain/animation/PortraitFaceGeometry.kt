package com.localcut.editor.domain.animation

import kotlin.math.abs

data class PortraitFaceSuggestion(
    val motion: PortraitMotionSettings,
    val brows: BrowExpressionSettings,
    val mouth: MouthRegion,
)

/** Conservative frontal-face geometry from two eye centers. No identity inference.
 * Coordinates are relative to the oriented source image, not screen/canvas pixels. */
object PortraitFaceGeometry {
    fun suggest(
        width: Int, height: Int,
        firstX: Double, firstY: Double, secondX: Double, secondY: Double,
        motion: PortraitMotionSettings = PortraitMotionSettings(),
        brows: BrowExpressionSettings = BrowExpressionSettings(),
    ): PortraitFaceSuggestion {
        require(width > 0 && height > 0)
        require(listOf(firstX, firstY, secondX, secondY).all { it.isFinite() && it in 0.0..1.0 })
        val left = minOf(firstX, secondX)
        val right = maxOf(firstX, secondX)
        val distance = right - left
        val distanceY = distance * width.toDouble() / height
        require(distance in 0.06..0.70 && abs(firstY - secondY) <= distanceY * 0.20) {
            "Marque dois olhos distintos de um rosto frontal"
        }
        val eyeY = (firstY + secondY) / 2.0
        val eyeRx = (distance * 0.28).coerceIn(0.02, 0.15)
        val eyeRy = (distanceY * 0.20).coerceIn(0.01, 0.09)
        val nextMotion = motion.copy(leftX = left, rightX = right, eyeY = eyeY,
            radiusX = eyeRx, radiusY = eyeRy).also { it.validate() }
        val nextBrows = brows.copy(leftX = left, rightX = right,
            browY = eyeY - distanceY * 0.30,
            radiusX = (distance * 0.30).coerceIn(0.02, 0.15),
            radiusY = (distanceY * 0.14).coerceIn(0.01, 0.09)).also { it.validate() }
        val mouth = MouthRegion((left + right) / 2.0, eyeY + distanceY * 0.78,
            (distance * 0.32).coerceIn(0.02, 0.20),
            (distanceY * 0.17).coerceIn(0.01, 0.10))
        require(mouth.centerX in mouth.radiusX..(1.0 - mouth.radiusX) &&
            mouth.centerY in mouth.radiusY..(1.0 - mouth.radiusY)) {
            "O rosto está parcialmente fora da imagem"
        }
        return PortraitFaceSuggestion(nextMotion, nextBrows, mouth)
    }
}
