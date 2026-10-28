package com.localcut.editor.media

import com.localcut.editor.domain.animation.PropertyAnimationEvaluator
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue

object PropertyTransformRenderResolver {
    private const val X = "transform.x"
    private const val Y = "transform.y"
    private const val SCALE = "transform.scale"
    private const val ROTATION = "transform.rotationDeg"
    private const val OPACITY = "transform.opacity"
    private val supportedProperties = setOf(X, Y, SCALE, ROTATION, OPACITY)

    fun valueAt(
        transform: TransformValue,
        keyframes: List<TransformKeyframe>,
        propertyAnimations: List<PropertyAnimation>,
        presentationTimeUs: Long,
        presentationStartUs: Long,
    ): TransformValue {
        val legacy = KeyframeRenderResolver.valueAt(
            transform = transform,
            keyframes = keyframes,
            presentationTimeUs = presentationTimeUs,
            presentationStartUs = presentationStartUs,
        )
        if (propertyAnimations.isEmpty()) return legacy

        val modern = propertyAnimations.filter { it.propertyId in supportedProperties }
        val ids = modern.map { it.propertyId }
        require(ids.distinct().size == ids.size) { "duplicate transform property animation" }
        val localTimeUs = if (presentationTimeUs <= presentationStartUs) {
            0L
        } else {
            presentationTimeUs - presentationStartUs
        }

        fun scalar(propertyId: String, fallback: Double): Double {
            val animation = modern.firstOrNull { it.propertyId == propertyId } ?: return fallback
            if (animation.keyframes.isEmpty()) return fallback
            require(animation.validationIssues().isEmpty()) { "invalid property animation: $propertyId" }
            val value = PropertyAnimationEvaluator.valueAt(animation, localTimeUs)
            return (value as? PropertyValue.Scalar)?.value
                ?: throw IllegalArgumentException("transform property animation must be scalar: $propertyId")
        }

        return TransformValue(
            x = scalar(X, legacy.x),
            y = scalar(Y, legacy.y),
            scale = scalar(SCALE, legacy.scale),
            rotationDeg = scalar(ROTATION, legacy.rotationDeg),
            opacity = scalar(OPACITY, legacy.opacity),
        )
    }
}
