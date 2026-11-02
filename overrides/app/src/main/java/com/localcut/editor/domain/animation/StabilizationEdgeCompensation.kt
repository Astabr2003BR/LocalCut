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

        // TransformEffectFactory converts domain pixels into Media3 normalized device
        // coordinates before Android Matrix postTranslate/postRotate/postScale calls.
        // Android post-concatenation yields M = S * R * T. For a target NDC corner q,
        // source coordinates are T^-1 * R^-1 * S^-1 * q. The rotated target square has
        // maximum component |cos| + |sin|. Translation happens before scale, so it
        // consumes a fixed fraction of the source NDC range and coverage is reciprocal.
        val tx = translationX / (canvasWidth / 2.0)
        val ty = translationY / (canvasHeight / 2.0)
        require(abs(tx) < 1.0 && abs(ty) < 1.0) {
            "STABILIZATION_EDGE_TRANSLATION_OUT_OF_RANGE"
        }

        val radians = rotationDeg * PI / 180.0
        val rotatedExtent = abs(cos(radians)) + abs(sin(radians))
        val scaleX = rotatedExtent / (1.0 - abs(tx))
        val scaleY = rotatedExtent / (1.0 - abs(ty))
        val required = max(1.0, max(scaleX, scaleY))
        require(required.isFinite()) { "stabilization edge scale invalid" }
        return required
    }
}
