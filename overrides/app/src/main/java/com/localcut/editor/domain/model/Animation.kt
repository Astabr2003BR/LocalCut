package com.localcut.editor.domain.model

enum class EasingKind {
    HOLD,
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_BEZIER,
    BOUNCE,
    ELASTIC,
    CYCLIC,
    RANDOM_SEEDED,
    STEPS,
}

data class CubicBezierCurve(
    val x1: Double = 0.25,
    val y1: Double = 0.1,
    val x2: Double = 0.25,
    val y2: Double = 1.0,
)

data class Easing(
    val kind: EasingKind = EasingKind.LINEAR,
    val bezier: CubicBezierCurve? = null,
    val steps: Int = 1,
    val seed: Long = 0L,
    val overshoot: Boolean = false,
) {
    fun validationIssues(): List<String> = buildList {
        if (kind == EasingKind.CUBIC_BEZIER && bezier == null) add("cubic bezier easing requires control points")
        bezier?.let { curve ->
            if (!curve.x1.isFinite() || !curve.y1.isFinite() || !curve.x2.isFinite() || !curve.y2.isFinite()) {
                add("cubic bezier control points must be finite")
            }
            if (curve.x1 !in 0.0..1.0 || curve.x2 !in 0.0..1.0) {
                add("cubic bezier x control points must be in 0..1")
            }
        }
        if (kind == EasingKind.STEPS && steps < 1) add("steps easing requires steps >= 1")
    }
}

sealed interface PropertyValue {
    data class Scalar(val value: Double) : PropertyValue
    data class Point(val x: Double, val y: Double) : PropertyValue
    data class Color(val argb: Int) : PropertyValue
    data class Flag(val value: Boolean) : PropertyValue

    fun validationIssues(): List<String> = when (this) {
        is Scalar -> if (value.isFinite()) emptyList() else listOf("scalar value must be finite")
        is Point -> if (x.isFinite() && y.isFinite()) emptyList() else listOf("point values must be finite")
        is Color, is Flag -> emptyList()
    }
}

data class PropertyKeyframe(
    val id: String,
    val offsetUs: Long,
    val value: PropertyValue,
    val easingToNext: Easing = Easing(),
)

data class PropertyAnimation(
    val propertyId: String,
    val keyframes: List<PropertyKeyframe> = emptyList(),
) {
    fun validationIssues(): List<String> = buildList {
        if (propertyId.isBlank()) add("propertyId must not be blank")
        if (keyframes.any { it.id.isBlank() }) add("keyframe id must not be blank")
        if (keyframes.map { it.id }.distinct().size != keyframes.size) add("keyframe ids must be unique")
        if (keyframes.any { it.offsetUs < 0L }) add("keyframe offsets must be non-negative")
        if (keyframes.zipWithNext().any { (left, right) -> right.offsetUs <= left.offsetUs }) {
            add("keyframe offsets must be strictly increasing")
        }
        keyframes.forEach { keyframe ->
            addAll(keyframe.value.validationIssues())
            addAll(keyframe.easingToNext.validationIssues())
        }
    }
}
