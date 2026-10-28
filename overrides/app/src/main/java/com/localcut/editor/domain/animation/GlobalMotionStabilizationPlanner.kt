package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue


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
    ): List<PropertyAnimation> {
        require(strength.isFinite() && strength in 0.0..1.0) { "stabilization strength must be in 0..1" }
        require(minConfidence.isFinite() && minConfidence in 0.0..1.0) { "stabilization confidence must be in 0..1" }
        if (samples.isEmpty()) return emptyList()
        validateSamples(samples)

        var cumulativeX = 0.0
        var cumulativeY = 0.0
        var cumulativeRotation = 0.0
        var cumulativeScale = 1.0
        val xValues = mutableListOf(0L to 0.0)
        val yValues = mutableListOf(0L to 0.0)
        val rotationValues = mutableListOf(0L to 0.0)
        val scaleValues = mutableListOf(0L to 1.0)

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

            xValues += sample.offsetUs to canonicalZero(-cumulativeX * strength)
            yValues += sample.offsetUs to canonicalZero(-cumulativeY * strength)
            rotationValues += sample.offsetUs to canonicalZero(-cumulativeRotation * strength)
            val inverseScale = 1.0 / cumulativeScale
            val correctionScale = 1.0 + (inverseScale - 1.0) * strength
            require(correctionScale.isFinite() && correctionScale > 0.0) { "stabilization scale invalid" }
            scaleValues += sample.offsetUs to correctionScale
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
