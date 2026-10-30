package com.localcut.editor.ui.editor

import com.localcut.editor.analysis.StabilizationApplyPolicy
import com.localcut.editor.domain.animation.MotionPathPlanner
import com.localcut.editor.domain.animation.MotionPathPoint
import com.localcut.editor.domain.design.CanvasPointPx
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.PropertyAnimation
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class MotionPathRange(
    val startOffsetUs: Long,
    val endOffsetUs: Long,
) {
    init {
        require(startOffsetUs >= 0L) { "motion path range start must be non-negative" }
        require(endOffsetUs > startOffsetUs) { "motion path range end must be after start" }
    }

    val durationUs: Long get() = endOffsetUs - startOffsetUs
}

object MotionPathUiPolicy {
    const val MAX_GESTURE_POINTS = 64
    private val motionProperties = setOf("transform.x", "transform.y")

    fun resolveRange(
        clipDurationUs: Long,
        startFraction: Double,
        endFraction: Double,
    ): MotionPathRange {
        require(clipDurationUs in 1L..MotionPathPlanner.MAX_OFFSET_US) { "motion path clip duration out of range" }
        require(startFraction.isFinite() && endFraction.isFinite()) { "motion path range fractions must be finite" }
        require(startFraction >= 0.0 && endFraction <= 1.0 && startFraction < endFraction) {
            "motion path range fractions must satisfy 0 <= start < end <= 1"
        }

        val start = floor(clipDurationUs.toDouble() * startFraction)
            .toLong()
            .coerceIn(0L, clipDurationUs - 1L)
        val end = ceil(clipDurationUs.toDouble() * endFraction)
            .toLong()
            .coerceIn(start + 1L, clipDurationUs)
        return MotionPathRange(start, end)
    }

    fun toMotionPoints(
        gesture: List<CanvasPointPx>,
        surfaceWidthPx: Double,
        surfaceHeightPx: Double,
        projectWidthPx: Double,
        projectHeightPx: Double,
        durationUs: Long,
        baseX: Double,
        baseY: Double,
        easing: Easing = Easing(),
        startOffsetUs: Long = 0L,
        endOffsetUs: Long = durationUs,
    ): List<MotionPathPoint> {
        require(surfaceWidthPx.isFinite() && surfaceWidthPx > 0.0) { "motion path surface width must be positive" }
        require(surfaceHeightPx.isFinite() && surfaceHeightPx > 0.0) { "motion path surface height must be positive" }
        require(projectWidthPx.isFinite() && projectWidthPx > 0.0) { "motion path project width must be positive" }
        require(projectHeightPx.isFinite() && projectHeightPx > 0.0) { "motion path project height must be positive" }
        require(durationUs in 1L..MotionPathPlanner.MAX_OFFSET_US) { "motion path clip duration out of range" }
        require(startOffsetUs >= 0L && endOffsetUs > startOffsetUs && endOffsetUs <= durationUs) {
            "motion path range must be inside clip duration"
        }
        require(baseX.isFinite() && baseY.isFinite()) { "motion path base transform must be finite" }
        require(easing.validationIssues().isEmpty()) { "motion path easing is invalid" }
        require(gesture.size >= 2) { "motion path gesture requires at least two points" }
        require(gesture.all { it.x.isFinite() && it.y.isFinite() }) { "motion path gesture points must be finite" }

        val rangeDurationUs = endOffsetUs - startOffsetUs
        val maxByDuration = (rangeDurationUs + 1L).coerceAtMost(MAX_GESTURE_POINTS.toLong()).toInt()
        val targetCount = minOf(gesture.size, MAX_GESTURE_POINTS, maxByDuration).coerceAtLeast(2)
        val sampled = if (gesture.size == targetCount) {
            gesture
        } else {
            (0 until targetCount).map { index ->
                val sourceIndex = (index.toDouble() * gesture.lastIndex / (targetCount - 1).toDouble())
                    .roundToInt()
                    .coerceIn(0, gesture.lastIndex)
                gesture[sourceIndex]
            }
        }
        val first = sampled.first()
        val lastIndex = sampled.lastIndex

        return sampled.mapIndexed { index, point ->
            MotionPathPoint(
                id = "ui-$index",
                offsetUs = startOffsetUs + index.toLong() * rangeDurationUs / lastIndex.toLong(),
                x = baseX + (point.x - first.x) / surfaceWidthPx * projectWidthPx,
                y = baseY + (point.y - first.y) / surfaceHeightPx * projectHeightPx,
                easingToNext = easing,
            )
        }
    }

    fun merge(
        existing: List<PropertyAnimation>,
        path: List<PropertyAnimation>,
    ): List<PropertyAnimation> {
        require(!StabilizationApplyPolicy.hasAnyGeneratedStabilization(existing)) {
            "MOTION_PATH_CONFLICTS_WITH_STABILIZATION"
        }
        require(path.map { it.propertyId }.toSet() == motionProperties && path.size == motionProperties.size) {
            "motion path must contain exactly transform.x and transform.y"
        }
        require(path.all { it.validationIssues().isEmpty() }) { "motion path animations must be valid" }
        val preserved = existing.filterNot { it.propertyId in motionProperties }
        val merged = preserved + path
        require(merged.map { it.propertyId }.distinct().size == merged.size) {
            "motion path merge produced duplicate properties"
        }
        return merged
    }

    fun hasGeneratedMotionPath(animations: List<PropertyAnimation>): Boolean {
        val byId = animations.associateBy { it.propertyId }
        val x = byId["transform.x"] ?: return false
        val y = byId["transform.y"] ?: return false
        return x.keyframes.isNotEmpty() && y.keyframes.isNotEmpty() &&
            x.keyframes.all { it.id.startsWith("motion-path-x-") } &&
            y.keyframes.all { it.id.startsWith("motion-path-y-") }
    }

    fun removeGeneratedMotionPath(animations: List<PropertyAnimation>): List<PropertyAnimation> =
        if (hasGeneratedMotionPath(animations)) {
            animations.filterNot { it.propertyId in motionProperties }
        } else {
            animations
        }
}
