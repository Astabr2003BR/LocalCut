package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue

data class ObjectTrackingPositionSample(
    val offsetUs: Long,
    val dxNorm: Double,
    val dyNorm: Double,
    val confidence: Double,
)

object ObjectTrackingPathPlanner {
    const val MAX_SAMPLES = 600
    const val MAX_OFFSET_US = 600_000_000L

    fun plan(
        samples: List<ObjectTrackingPositionSample>,
        renderWidthPx: Double,
        renderHeightPx: Double,
        baseX: Double,
        baseY: Double,
        minConfidence: Double = 0.35,
    ): List<PropertyAnimation> {
        require(samples.size in 1..MAX_SAMPLES) { "object tracking requires 1..$MAX_SAMPLES samples" }
        require(renderWidthPx.isFinite() && renderWidthPx > 0.0) { "object tracking render width must be positive" }
        require(renderHeightPx.isFinite() && renderHeightPx > 0.0) { "object tracking render height must be positive" }
        require(baseX.isFinite() && baseY.isFinite()) { "object tracking base transform must be finite" }
        require(minConfidence.isFinite() && minConfidence in 0.0..1.0) {
            "object tracking confidence threshold must be in 0..1"
        }
        require(samples.first().offsetUs > 0L) { "object tracking samples must start after zero" }
        require(samples.all { it.offsetUs in 1L..MAX_OFFSET_US }) { "object tracking sample offset out of range" }
        require(samples.zipWithNext().all { (left, right) -> right.offsetUs > left.offsetUs }) {
            "object tracking sample offsets must be strictly increasing"
        }
        samples.forEach { sample ->
            require(sample.dxNorm.isFinite() && sample.dyNorm.isFinite() && sample.confidence.isFinite()) {
                "object tracking sample values must be finite"
            }
            require(sample.confidence in 0.0..1.0) { "object tracking sample confidence must be in 0..1" }
        }
        require(samples.any { it.confidence >= minConfidence }) { "OBJECT_TRACKING_CONFIDENCE_TOO_LOW" }

        var x = baseX
        var y = baseY
        val xValues = mutableListOf(0L to x)
        val yValues = mutableListOf(0L to y)
        samples.forEach { sample ->
            if (sample.confidence >= minConfidence) {
                x = baseX + sample.dxNorm * renderWidthPx
                y = baseY + sample.dyNorm * renderHeightPx
            }
            xValues += sample.offsetUs to canonicalZero(x)
            yValues += sample.offsetUs to canonicalZero(y)
        }

        return listOf(
            scalarAnimation("transform.x", "object-track-x", xValues),
            scalarAnimation("transform.y", "object-track-y", yValues),
        )
    }

    private fun canonicalZero(value: Double): Double = if (value == 0.0) 0.0 else value

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
        require(animation.validationIssues().isEmpty()) { "object tracking produced invalid animation" }
    }
}
