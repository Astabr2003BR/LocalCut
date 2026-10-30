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
        compositionContainer: CompositionTransformContainer? = null,
        compositionContainerTimeline: CompositionTransformTimeline? = null,
    ): TransformValue {
        val legacyWorld = KeyframeRenderResolver.valueAt(
            transform = transform,
            keyframes = keyframes,
            presentationTimeUs = presentationTimeUs,
            presentationStartUs = presentationStartUs,
        )
        if (propertyAnimations.isEmpty() && compositionContainerTimeline == null) return legacyWorld

        val modern = propertyAnimations.filter { it.propertyId in supportedProperties }
        if (modern.isNotEmpty()) {
            val ids = modern.map { it.propertyId }
            require(ids.distinct().size == ids.size) { "duplicate transform property animation" }
        }

        val legacyLocal = compositionContainer?.let { container ->
            CompositionTransformMath.remove(container, legacyWorld)
        } ?: legacyWorld
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

        val local = TransformValue(
            x = scalar(X, legacyLocal.x),
            y = scalar(Y, legacyLocal.y),
            scale = scalar(SCALE, legacyLocal.scale),
            rotationDeg = scalar(ROTATION, legacyLocal.rotationDeg),
            opacity = scalar(OPACITY, legacyLocal.opacity),
        )
        val activeContainer = compositionContainerTimeline?.containerAt(presentationTimeUs)
            ?: compositionContainer
        return activeContainer?.let { container ->
            CompositionTransformMath.apply(container, local)
        } ?: local
    }
}
