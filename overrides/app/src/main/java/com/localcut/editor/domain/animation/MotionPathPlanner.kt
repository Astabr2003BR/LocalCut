package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue

data class MotionPathPoint(
    val id: String,
    val offsetUs: Long,
    val x: Double,
    val y: Double,
    val easingToNext: Easing = Easing(),
)

object MotionPathPlanner {
    const val MAX_POINTS = 256
    const val MAX_OFFSET_US = 600_000_000L

    fun plan(points: List<MotionPathPoint>): List<PropertyAnimation> {
        require(points.size in 2..MAX_POINTS) { "motion path requires 2..$MAX_POINTS points" }
        require(points.all { it.id.isNotBlank() }) { "motion path point ids must not be blank" }
        require(points.map { it.id }.distinct().size == points.size) { "motion path point ids must be unique" }
        require(points.all { it.offsetUs in 0L..MAX_OFFSET_US }) { "motion path point offset out of range" }
        require(points.zipWithNext().all { (left, right) -> right.offsetUs > left.offsetUs }) {
            "motion path point offsets must be strictly increasing"
        }
        points.forEach { point ->
            require(point.x.isFinite() && point.y.isFinite()) { "motion path coordinates must be finite" }
            require(point.easingToNext.validationIssues().isEmpty()) { "motion path easing is invalid" }
        }

        val xAnimation = scalarAnimation(
            propertyId = "transform.x",
            points = points,
            value = { it.x },
        )
        val yAnimation = scalarAnimation(
            propertyId = "transform.y",
            points = points,
            value = { it.y },
        )
        return listOf(xAnimation, yAnimation)
    }

    private fun scalarAnimation(
        propertyId: String,
        points: List<MotionPathPoint>,
        value: (MotionPathPoint) -> Double,
    ): PropertyAnimation = PropertyAnimation(
        propertyId = propertyId,
        keyframes = points.map { point ->
            PropertyKeyframe(
                id = "motion-path-${propertyId.substringAfterLast('.')}-${point.id}",
                offsetUs = point.offsetUs,
                value = PropertyValue.Scalar(value(point)),
                easingToNext = point.easingToNext,
            )
        },
    ).also { animation ->
        require(animation.validationIssues().isEmpty()) { "motion path produced invalid animation" }
    }
}
