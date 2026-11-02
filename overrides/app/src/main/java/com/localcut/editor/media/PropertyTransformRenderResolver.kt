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

    class Compiled internal constructor(
        private val transform: TransformValue,
        private val keyframes: List<TransformKeyframe>,
        private val modernByProperty: Map<String, PropertyAnimation>,
        private val presentationStartUs: Long,
        private val compositionContainer: CompositionTransformContainer?,
        private val compositionContainerTimeline: CompositionTransformTimeline?,
    ) {
        fun valueAt(presentationTimeUs: Long): TransformValue {
            val legacyWorld = KeyframeRenderResolver.valueAt(
                transform = transform,
                keyframes = keyframes,
                presentationTimeUs = presentationTimeUs,
                presentationStartUs = presentationStartUs,
            )
            if (modernByProperty.isEmpty() && compositionContainerTimeline == null) return legacyWorld

            val legacyLocal = compositionContainer?.let { container ->
                CompositionTransformMath.remove(container, legacyWorld)
            } ?: legacyWorld
            val localTimeUs = if (presentationTimeUs <= presentationStartUs) {
                0L
            } else {
                presentationTimeUs - presentationStartUs
            }

            fun scalar(propertyId: String, fallback: Double): Double {
                val animation = modernByProperty[propertyId] ?: return fallback
                if (animation.keyframes.isEmpty()) return fallback
                val value = PropertyAnimationEvaluator.valueAtValidated(animation, localTimeUs)
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

    fun compile(
        transform: TransformValue,
        keyframes: List<TransformKeyframe>,
        propertyAnimations: List<PropertyAnimation>,
        presentationStartUs: Long,
        compositionContainer: CompositionTransformContainer? = null,
        compositionContainerTimeline: CompositionTransformTimeline? = null,
    ): Compiled {
        val modern = propertyAnimations.filter { it.propertyId in supportedProperties }
        if (modern.isNotEmpty()) {
            val ids = modern.map { it.propertyId }
            require(ids.distinct().size == ids.size) { "duplicate transform property animation" }
            modern.forEach { animation ->
                val issues = animation.validationIssues()
                require(issues.isEmpty()) { "invalid property animation: ${animation.propertyId}: ${issues.first()}" }
            }
        }
        return Compiled(
            transform = transform,
            keyframes = keyframes,
            modernByProperty = modern.associateBy { it.propertyId },
            presentationStartUs = presentationStartUs,
            compositionContainer = compositionContainer,
            compositionContainerTimeline = compositionContainerTimeline,
        )
    }

    fun valueAt(
        transform: TransformValue,
        keyframes: List<TransformKeyframe>,
        propertyAnimations: List<PropertyAnimation>,
        presentationTimeUs: Long,
        presentationStartUs: Long,
        compositionContainer: CompositionTransformContainer? = null,
        compositionContainerTimeline: CompositionTransformTimeline? = null,
    ): TransformValue = compile(
        transform = transform,
        keyframes = keyframes,
        propertyAnimations = propertyAnimations,
        presentationStartUs = presentationStartUs,
        compositionContainer = compositionContainer,
        compositionContainerTimeline = compositionContainerTimeline,
    ).valueAt(presentationTimeUs)
}
