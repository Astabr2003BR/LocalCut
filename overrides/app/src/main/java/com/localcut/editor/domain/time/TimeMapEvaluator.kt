package com.localcut.editor.domain.time

import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import kotlin.math.roundToLong

object TimeMapEvaluator {
    fun sourceOffsetAt(timeMap: TimeMap, presentationOffsetUs: Long, sourceRangeUs: Long): Long {
        require(sourceRangeUs >= 0L) { "sourceRangeUs must be non-negative" }
        require(presentationOffsetUs >= 0L) { "presentationOffsetUs must be non-negative" }
        require(timeMap.validationIssues().isEmpty()) { "invalid time map: ${timeMap.validationIssues().first()}" }

        val freeze = timeMap.freezeSegments.firstOrNull { segment ->
            presentationOffsetUs >= segment.startOffsetUs &&
                presentationOffsetUs < safeAdd(segment.startOffsetUs, segment.durationUs)
        }
        val forward = if (freeze != null) {
            freeze.sourceOffsetUs.coerceIn(0L, sourceRangeUs)
        } else {
            forwardSourceOffsetAt(timeMap, presentationOffsetUs).coerceIn(0L, sourceRangeUs)
        }
        return if (timeMap.reverse) sourceRangeUs - forward else forward
    }

    fun forwardSourceOffsetAt(timeMap: TimeMap, presentationOffsetUs: Long): Long {
        require(presentationOffsetUs >= 0L) { "presentationOffsetUs must be non-negative" }
        require(timeMap.validationIssues().isEmpty()) { "invalid time map: ${timeMap.validationIssues().first()}" }
        var sourceUs = integrateSpeed(timeMap, 0L, presentationOffsetUs)
        timeMap.freezeSegments.forEach { freeze ->
            val overlapStart = maxOf(0L, freeze.startOffsetUs)
            val overlapEnd = minOf(presentationOffsetUs, safeAdd(freeze.startOffsetUs, freeze.durationUs))
            if (overlapEnd > overlapStart) {
                sourceUs -= integrateSpeed(timeMap, overlapStart, overlapEnd)
            }
        }
        return sourceUs.coerceAtLeast(0.0).roundToLong()
    }

    fun presentationDurationUs(sourceRangeUs: Long, timeMap: TimeMap): Long {
        require(sourceRangeUs > 0L) { "sourceRangeUs must be positive" }
        require(timeMap.validationIssues().isEmpty()) { "invalid time map: ${timeMap.validationIssues().first()}" }

        if (timeMap.speedPoints.isEmpty()) {
            val movingDuration = (sourceRangeUs.toDouble() / timeMap.baseSpeed).roundToLong().coerceAtLeast(1L)
            return timeMap.freezeSegments.fold(movingDuration) { total, freeze -> safeAdd(total, freeze.durationUs) }
        }

        var low = 0L
        var high = maxOf(1L, (sourceRangeUs.toDouble() / timeMap.baseSpeed).roundToLong())
        val freezeBudget = timeMap.freezeSegments.sumOf { it.durationUs }
        high = safeAdd(high, freezeBudget)
        var guard = 0
        while (forwardSourceOffsetAt(timeMap, high) < sourceRangeUs && guard < 62) {
            high = if (high > Long.MAX_VALUE / 2L) Long.MAX_VALUE else high * 2L
            guard++
            if (high == Long.MAX_VALUE) break
        }
        require(forwardSourceOffsetAt(timeMap, high) >= sourceRangeUs) { "time map cannot reach requested source range" }

        while (low < high) {
            val mid = low + (high - low) / 2L
            if (forwardSourceOffsetAt(timeMap, mid) >= sourceRangeUs) {
                high = mid
            } else {
                low = mid + 1L
            }
        }
        return low.coerceAtLeast(1L)
    }

    private fun integrateSpeed(timeMap: TimeMap, startUs: Long, endUs: Long): Double {
        if (endUs <= startUs) return 0.0
        val boundaries = buildList {
            add(startUs)
            timeMap.speedPoints.forEach { point ->
                if (point.offsetUs > startUs && point.offsetUs < endUs) add(point.offsetUs)
            }
            add(endUs)
        }.distinct().sorted()

        var source = 0.0
        boundaries.zipWithNext().forEach { (left, right) ->
            val speedLeft = speedAt(timeMap, left)
            val speedRight = speedAt(timeMap, right)
            val duration = (right - left).toDouble()
            source += (speedLeft + speedRight) * 0.5 * duration
        }
        return source
    }

    private fun speedAt(timeMap: TimeMap, offsetUs: Long): Double {
        val points = timeMap.speedPoints
        if (points.isEmpty()) return timeMap.baseSpeed
        if (offsetUs < points.first().offsetUs) return timeMap.baseSpeed
        if (offsetUs == points.first().offsetUs) return points.first().speed
        if (offsetUs >= points.last().offsetUs) return points.last().speed

        val rightIndex = points.indexOfFirst { it.offsetUs >= offsetUs }
        val right: SpeedPoint = points[rightIndex]
        if (right.offsetUs == offsetUs) return right.speed
        val left: SpeedPoint = points[rightIndex - 1]
        val t = (offsetUs - left.offsetUs).toDouble() / (right.offsetUs - left.offsetUs).toDouble()
        return left.speed + (right.speed - left.speed) * t
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}
