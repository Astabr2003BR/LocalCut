package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformValue


data class GlobalMotionSample(
    val offsetUs: Long,
    val dx: Double,
    val dy: Double,
    val rotationDeg: Double,
    val scale: Double,
    val confidence: Double,
)

object GlobalMotionStabilizationPlanner {
    private const val X = "transform.x"
    private const val Y = "transform.y"
    private const val ROTATION = "transform.rotationDeg"
    private const val SCALE = "transform.scale"

    fun plan(
        samples: List<GlobalMotionSample>,
        strength: Double = 1.0,
        minConfidence: Double = 0.5,
        baseTransform: TransformValue = TransformValue(),
        canvasWidth: Int? = null,
        canvasHeight: Int? = null,
        maxEdgeCompensationScale: Double = 2.0,
    ): List<PropertyAnimation> {
        require(strength.isFinite() && strength in 0.0..1.0) { "stabilization strength must be in 0..1" }
        require(minConfidence.isFinite() && minConfidence in 0.0..1.0) { "stabilization confidence must be in 0..1" }
        require(
            baseTransform.x.isFinite() &&
                baseTransform.y.isFinite() &&
                baseTransform.rotationDeg.isFinite() &&
                baseTransform.scale.isFinite() &&
                baseTransform.scale > 0.0
        ) { "stabilization base transform invalid" }
        require((canvasWidth == null) == (canvasHeight == null)) {
            "stabilization canvas dimensions must be provided together"
        }
        if (canvasWidth != null && canvasHeight != null) {
            require(canvasWidth > 0 && canvasHeight > 0) { "stabilization canvas must be positive" }
            require(maxEdgeCompensationScale.isFinite() && maxEdgeCompensationScale >= 1.0) {
                "stabilization max edge compensation scale invalid"
            }
        }
        if (samples.isEmpty()) return emptyList()
        validateSamples(samples)

        var cumulativeX = 0.0
        var cumulativeY = 0.0
        var cumulativeRotation = 0.0
        var cumulativeScale = 1.0
        val xValues = mutableListOf(0L to canonicalZero(baseTransform.x))
        val yValues = mutableListOf(0L to canonicalZero(baseTransform.y))
        val rotationValues = mutableListOf(0L to canonicalZero(baseTransform.rotationDeg))
        val scaleValues = mutableListOf(0L to baseTransform.scale)

        samples.forEach { sample ->
            if (sample.confidence >= minConfidence) {
                cumulativeX += sample.dx
                cumulativeY += sample.dy
                cumulativeRotation += sample.rotationDeg
                cumulativeScale *= sample.scale
                require(
                    cumulativeX.isFinite() &&
                        cumulativeY.isFinite() &&
                        cumulativeRotation.isFinite() &&
                        cumulativeScale.isFinite() &&
                        cumulativeScale > 0.0
                ) { "stabilization accumulation overflow" }
            }

            val correctionX = canonicalZero(-cumulativeX * strength)
            val correctionY = canonicalZero(-cumulativeY * strength)
            val correctionRotation = canonicalZero(-cumulativeRotation * strength)
            val inverseScale = 1.0 / cumulativeScale
            val correctionScale = 1.0 + (inverseScale - 1.0) * strength
            require(correctionScale.isFinite() && correctionScale > 0.0) { "stabilization scale invalid" }

            val edgeScale = if (canvasWidth != null && canvasHeight != null) {
                try {
                    StabilizationEdgeCompensation.requiredCoverageScale(
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        translationX = correctionX,
                        translationY = correctionY,
                        rotationDeg = correctionRotation,
                    )
                } catch (error: IllegalArgumentException) {
                    if (error.message == "STABILIZATION_EDGE_TRANSLATION_OUT_OF_RANGE") {
                        throw IllegalArgumentException("STABILIZATION_EDGE_COMPENSATION_TOO_LARGE", error)
                    }
                    throw error
                }
            } else {
                1.0
            }
            val relativeScale = maxOf(correctionScale, edgeScale)
            if (canvasWidth != null) {
                require(relativeScale <= maxEdgeCompensationScale) {
                    "STABILIZATION_EDGE_COMPENSATION_TOO_LARGE"
                }
            }
            val finalScale = baseTransform.scale * relativeScale
            require(finalScale.isFinite() && finalScale > 0.0) { "stabilization scale invalid" }

            xValues += sample.offsetUs to canonicalZero(baseTransform.x + correctionX)
            yValues += sample.offsetUs to canonicalZero(baseTransform.y + correctionY)
            rotationValues += sample.offsetUs to canonicalZero(baseTransform.rotationDeg + correctionRotation)
            scaleValues += sample.offsetUs to finalScale
        }

        return listOf(
            scalarAnimation(X, "stabilize-x", xValues),
            scalarAnimation(Y, "stabilize-y", yValues),
            scalarAnimation(ROTATION, "stabilize-rotation", rotationValues),
            scalarAnimation(SCALE, "stabilize-scale", scaleValues),
        )
    }

    private fun canonicalZero(value: Double): Double = if (value == 0.0) 0.0 else value

    private fun validateSamples(samples: List<GlobalMotionSample>) {
        require(samples.first().offsetUs > 0L) { "stabilization samples must start after zero" }
        require(samples.zipWithNext().all { (left, right) -> right.offsetUs > left.offsetUs }) {
            "stabilization sample offsets must be strictly increasing"
        }
        samples.forEach { sample ->
            require(
                sample.dx.isFinite() &&
                    sample.dy.isFinite() &&
                    sample.rotationDeg.isFinite() &&
                    sample.scale.isFinite() &&
                    sample.confidence.isFinite()
            ) { "stabilization sample values must be finite" }
            require(sample.scale > 0.0) { "stabilization sample scale must be positive" }
            require(sample.confidence in 0.0..1.0) { "stabilization sample confidence must be in 0..1" }
        }
    }

    private fun scalarAnimation(
        propertyId: String,
        keyPrefix: String,
        values: List<Pair<Long, Double>>,
    ): PropertyAnimation = PropertyAnimation(
        propertyId = propertyId,
        keyframes = values.mapIndexed { index, (offsetUs, value) ->
            PropertyKeyframe(
                id = "$keyPrefix-$index-$offsetUs",
                offsetUs = offsetUs,
                value = PropertyValue.Scalar(value),
                easingToNext = Easing(EasingKind.LINEAR),
            )
        },
    ).also { animation ->
        require(animation.validationIssues().isEmpty()) { "stabilization produced invalid animation" }
    }
}
