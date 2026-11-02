package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue

enum class MotionOperationKind(val propertyId: String) {
    MOVE_X("transform.x"),
    MOVE_Y("transform.y"),
    SCALE("transform.scale"),
    ROTATE("transform.rotationDeg"),
    OPACITY("transform.opacity"),
}

data class MotionOperationSpec(
    val id: String,
    val kind: MotionOperationKind,
    val startOffsetUs: Long,
    val durationUs: Long,
    val from: Double,
    val to: Double,
    val easing: Easing = Easing(),
)

object MotionOperationPlanner {
    const val MAX_DURATION_US = 600_000_000L

    fun toAnimation(spec: MotionOperationSpec): PropertyAnimation {
        require(spec.id.isNotBlank()) { "motion operation id must not be blank" }
        require(spec.startOffsetUs >= 0L) { "motion operation start must be non-negative" }
        require(spec.durationUs in 1L..MAX_DURATION_US) { "motion operation duration out of range" }
        require(spec.from.isFinite() && spec.to.isFinite()) { "motion operation values must be finite" }
        require(spec.easing.validationIssues().isEmpty()) { "motion operation easing is invalid" }
        when (spec.kind) {
            MotionOperationKind.SCALE -> require(spec.from > 0.0 && spec.to > 0.0) {
                "scale operation values must be positive"
            }
            MotionOperationKind.OPACITY -> require(spec.from in 0.0..1.0 && spec.to in 0.0..1.0) {
                "opacity operation values must be in 0..1"
            }
            MotionOperationKind.MOVE_X,
            MotionOperationKind.MOVE_Y,
            MotionOperationKind.ROTATE,
            -> Unit
        }

        val endOffsetUs = Math.addExact(spec.startOffsetUs, spec.durationUs)
        return PropertyAnimation(
            propertyId = spec.kind.propertyId,
            keyframes = listOf(
                PropertyKeyframe(
                    id = "${spec.id}-start",
                    offsetUs = spec.startOffsetUs,
                    value = PropertyValue.Scalar(spec.from),
                    easingToNext = spec.easing,
                ),
                PropertyKeyframe(
                    id = "${spec.id}-end",
                    offsetUs = endOffsetUs,
                    value = PropertyValue.Scalar(spec.to),
                ),
            ),
        ).also { animation ->
            require(animation.validationIssues().isEmpty()) { "motion operation produced invalid animation" }
        }
    }
}
