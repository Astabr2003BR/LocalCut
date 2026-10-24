package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CubicBezierCurve
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyAnimation

object AnimationCurveUiPolicy {
    fun applyKind(
        source: List<PropertyAnimation>,
        kind: EasingKind,
    ): List<PropertyAnimation> {
        require(kind != EasingKind.CUBIC_BEZIER) { "use applyCubicBezier for cubic bezier easing" }
        require(kind != EasingKind.STEPS) { "use applySteps for steps easing" }
        return mapEasing(source) { previous ->
            Easing(
                kind = kind,
                seed = previous.seed,
                overshoot = previous.overshoot,
            )
        }
    }

    fun applyCubicBezier(
        source: List<PropertyAnimation>,
        curve: CubicBezierCurve,
    ): List<PropertyAnimation> {
        val easing = Easing(kind = EasingKind.CUBIC_BEZIER, bezier = curve)
        require(easing.validationIssues().isEmpty()) { easing.validationIssues().first() }
        return mapEasing(source) { previous ->
            easing.copy(seed = previous.seed, overshoot = previous.overshoot)
        }
    }

    fun applySteps(
        source: List<PropertyAnimation>,
        steps: Int,
    ): List<PropertyAnimation> {
        require(steps >= 1) { "steps must be >= 1" }
        return mapEasing(source) { previous ->
            Easing(
                kind = EasingKind.STEPS,
                steps = steps,
                seed = previous.seed,
                overshoot = previous.overshoot,
            )
        }
    }

    private inline fun mapEasing(
        source: List<PropertyAnimation>,
        transform: (Easing) -> Easing,
    ): List<PropertyAnimation> = source.map { animation ->
        animation.copy(
            keyframes = animation.keyframes.map { keyframe ->
                keyframe.copy(easingToNext = transform(keyframe.easingToNext))
            },
        )
    }
}
