package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.CubicBezierCurve
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object PropertyAnimationEvaluator {
    fun valueAt(animation: PropertyAnimation, offsetUs: Long): PropertyValue {
        require(animation.validationIssues().isEmpty()) { "invalid property animation: ${animation.validationIssues().first()}" }
        require(animation.keyframes.isNotEmpty()) { "property animation requires at least one keyframe" }
        val keyframes = animation.keyframes
        if (keyframes.size == 1 || offsetUs <= keyframes.first().offsetUs) return keyframes.first().value
        if (offsetUs >= keyframes.last().offsetUs) return keyframes.last().value

        val rightIndex = keyframes.indexOfFirst { it.offsetUs >= offsetUs }
        val right = keyframes[rightIndex]
        if (right.offsetUs == offsetUs) return right.value
        val left = keyframes[rightIndex - 1]
        require(sameValueType(left.value, right.value)) { "property animation keyframe types must match" }

        val spanUs = right.offsetUs - left.offsetUs
        val linearT = (offsetUs - left.offsetUs).toDouble() / spanUs.toDouble()
        val easedT = easingProgress(left.easingToNext, linearT, left, right, offsetUs)
        return interpolate(left.value, right.value, easedT)
    }

    private fun easingProgress(
        easing: Easing,
        t: Double,
        left: PropertyKeyframe,
        right: PropertyKeyframe,
        offsetUs: Long,
    ): Double {
        val clamped = t.coerceIn(0.0, 1.0)
        val raw = when (easing.kind) {
            EasingKind.HOLD -> 0.0
            EasingKind.LINEAR -> clamped
            EasingKind.EASE_IN -> clamped * clamped
            EasingKind.EASE_OUT -> 1.0 - (1.0 - clamped) * (1.0 - clamped)
            EasingKind.EASE_IN_OUT -> if (clamped < 0.5) {
                2.0 * clamped * clamped
            } else {
                1.0 - (-2.0 * clamped + 2.0).pow(2.0) / 2.0
            }
            EasingKind.CUBIC_BEZIER -> cubicBezierProgress(requireNotNull(easing.bezier), clamped)
            EasingKind.BOUNCE -> bounceOut(clamped)
            EasingKind.ELASTIC -> elasticOut(clamped)
            EasingKind.CYCLIC -> clamped + sin(2.0 * PI * clamped) * 0.1
            EasingKind.RANDOM_SEEDED -> seededProgress(easing.seed, left, right, offsetUs)
            EasingKind.STEPS -> floor(clamped * easing.steps.toDouble()) / easing.steps.toDouble()
        }
        return if (easing.overshoot) raw else raw.coerceIn(0.0, 1.0)
    }

    private fun cubicBezierProgress(curve: CubicBezierCurve, t: Double): Double {
        var low = 0.0
        var high = 1.0
        var u = t
        repeat(24) {
            val x = cubic(u, 0.0, curve.x1, curve.x2, 1.0)
            if (x < t) low = u else high = u
            u = (low + high) / 2.0
        }
        return cubic(u, 0.0, curve.y1, curve.y2, 1.0)
    }

    private fun cubic(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
        val oneMinus = 1.0 - t
        return oneMinus.pow(3.0) * p0 +
            3.0 * oneMinus.pow(2.0) * t * p1 +
            3.0 * oneMinus * t * t * p2 +
            t.pow(3.0) * p3
    }

    private fun bounceOut(t: Double): Double {
        val n1 = 7.5625
        val d1 = 2.75
        return when {
            t < 1.0 / d1 -> n1 * t * t
            t < 2.0 / d1 -> {
                val shifted = t - 1.5 / d1
                n1 * shifted * shifted + 0.75
            }
            t < 2.5 / d1 -> {
                val shifted = t - 2.25 / d1
                n1 * shifted * shifted + 0.9375
            }
            else -> {
                val shifted = t - 2.625 / d1
                n1 * shifted * shifted + 0.984375
            }
        }
    }

    private fun elasticOut(t: Double): Double {
        if (t == 0.0 || t == 1.0) return t
        val c4 = (2.0 * PI) / 3.0
        return 2.0.pow(-10.0 * t) * sin((t * 10.0 - 0.75) * c4) + 1.0
    }

    private fun seededProgress(
        seed: Long,
        left: PropertyKeyframe,
        right: PropertyKeyframe,
        offsetUs: Long,
    ): Double {
        val mixed = seed xor left.offsetUs xor java.lang.Long.rotateLeft(right.offsetUs, 17) xor java.lang.Long.rotateLeft(offsetUs, 33)
        return Random(mixed).nextDouble()
    }

    private fun interpolate(left: PropertyValue, right: PropertyValue, t: Double): PropertyValue = when {
        left is PropertyValue.Scalar && right is PropertyValue.Scalar ->
            PropertyValue.Scalar(lerp(left.value, right.value, t))
        left is PropertyValue.Point && right is PropertyValue.Point ->
            PropertyValue.Point(lerp(left.x, right.x, t), lerp(left.y, right.y, t))
        left is PropertyValue.Color && right is PropertyValue.Color ->
            PropertyValue.Color(interpolateColor(left.argb, right.argb, t))
        left is PropertyValue.Flag && right is PropertyValue.Flag ->
            if (t < 1.0) left else right
        else -> error("property animation keyframe types must match")
    }

    private fun sameValueType(left: PropertyValue, right: PropertyValue): Boolean =
        (left is PropertyValue.Scalar && right is PropertyValue.Scalar) ||
            (left is PropertyValue.Point && right is PropertyValue.Point) ||
            (left is PropertyValue.Color && right is PropertyValue.Color) ||
            (left is PropertyValue.Flag && right is PropertyValue.Flag)

    private fun interpolateColor(left: Int, right: Int, t: Double): Int {
        fun channel(value: Int, shift: Int): Int = (value ushr shift) and 0xFF
        fun mix(shift: Int): Int = lerp(channel(left, shift).toDouble(), channel(right, shift).toDouble(), t)
            .roundToInt()
            .coerceIn(0, 255)
        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}

object LegacyTransformAnimationAdapter {
    private const val X = "transform.x"
    private const val Y = "transform.y"
    private const val SCALE = "transform.scale"
    private const val ROTATION = "transform.rotationDeg"
    private const val OPACITY = "transform.opacity"

    fun fromLegacy(keyframes: List<TransformKeyframe>): List<PropertyAnimation> {
        if (keyframes.isEmpty()) return emptyList()
        val sorted = keyframes.sortedBy { it.offsetUs }
        require(sorted.zipWithNext().none { (left, right) -> left.offsetUs == right.offsetUs }) {
            "duplicate keyframe timestamp"
        }
        fun animation(propertyId: String, selector: (TransformValue) -> Double): PropertyAnimation = PropertyAnimation(
            propertyId = propertyId,
            keyframes = sorted.mapIndexed { index, keyframe ->
                PropertyKeyframe(
                    id = "legacy-$propertyId-$index-${keyframe.offsetUs}",
                    offsetUs = keyframe.offsetUs,
                    value = PropertyValue.Scalar(selector(keyframe.value)),
                    easingToNext = Easing(EasingKind.LINEAR),
                )
            },
        )
        return listOf(
            animation(X) { it.x },
            animation(Y) { it.y },
            animation(SCALE) { it.scale },
            animation(ROTATION) { it.rotationDeg },
            animation(OPACITY) { it.opacity },
        )
    }

    fun valueAt(animations: List<PropertyAnimation>, offsetUs: Long): TransformValue {
        fun scalar(propertyId: String, fallback: Double): Double {
            val animation = animations.firstOrNull { it.propertyId == propertyId } ?: return fallback
            return (PropertyAnimationEvaluator.valueAt(animation, offsetUs) as PropertyValue.Scalar).value
        }
        return TransformValue(
            x = scalar(X, 0.0),
            y = scalar(Y, 0.0),
            scale = scalar(SCALE, 1.0),
            rotationDeg = scalar(ROTATION, 0.0),
            opacity = scalar(OPACITY, 1.0),
        )
    }
}
