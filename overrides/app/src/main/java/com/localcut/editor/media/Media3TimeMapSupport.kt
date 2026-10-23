package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.time.TimeMapEvaluator
import kotlin.math.max

@OptIn(UnstableApi::class)
internal class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
    init {
        require(speed.isFinite() && speed > 0f) { "speed must be finite and positive" }
    }

    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

internal data class SpeedSegment(
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val speed: Float,
) {
    init {
        require(sourceStartUs >= 0L) { "sourceStartUs must be non-negative" }
        require(sourceEndUs > sourceStartUs) { "sourceEndUs must be greater than sourceStartUs" }
        require(speed.isFinite() && speed > 0f) { "speed must be finite and positive" }
    }
}

@OptIn(UnstableApi::class)
internal class SegmentedSpeedProvider(
    val segments: List<SpeedSegment>,
) : SpeedProvider {
    init {
        require(segments.isNotEmpty()) { "speed segments must not be empty" }
        require(segments.first().sourceStartUs == 0L) { "speed segments must start at source offset 0" }
        require(segments.zipWithNext().all { (left, right) -> left.sourceEndUs == right.sourceStartUs }) {
            "speed segments must be contiguous"
        }
    }

    override fun getSpeed(timeUs: Long): Float {
        require(timeUs >= 0L) { "timeUs must be non-negative" }
        val index = segments.binarySearchBy(timeUs) { it.sourceStartUs }
        if (index >= 0) return segments[index].speed
        val insertionPoint = -index - 1
        return segments[(insertionPoint - 1).coerceIn(0, segments.lastIndex)].speed
    }

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
        require(timeUs >= 0L) { "timeUs must be non-negative" }
        val next = segments.firstOrNull { it.sourceStartUs > timeUs }
        return next?.sourceStartUs ?: C.TIME_UNSET
    }
}

internal object Media3TimeMapSupport {
    private const val TARGET_PRESENTATION_STEP_US = 20_000L
    private const val MAX_SEGMENTS = 20_000L

    fun canRender(timeMap: TimeMap): Boolean =
        timeMap.validationIssues().isEmpty() &&
            !timeMap.reverse &&
            timeMap.freezeSegments.isEmpty()

    fun providerOrNull(timeMap: TimeMap, sourceRangeUs: Long): SpeedProvider? {
        require(sourceRangeUs > 0L) { "sourceRangeUs must be positive" }
        if (!canRender(timeMap)) return null
        if (timeMap.speedPoints.isEmpty()) {
            return if (timeMap.baseSpeed == 1.0) null else ConstantSpeedProvider(timeMap.baseSpeed.toFloat())
        }
        return compileRampProvider(timeMap, sourceRangeUs)
    }

    /** Compatibility overload retained for callers that only support constant speed. */
    fun providerOrNull(timeMap: TimeMap): SpeedProvider? {
        if (!canRender(timeMap) || timeMap.speedPoints.isNotEmpty()) return null
        return if (timeMap.baseSpeed == 1.0) null else ConstantSpeedProvider(timeMap.baseSpeed.toFloat())
    }

    private fun compileRampProvider(timeMap: TimeMap, sourceRangeUs: Long): SegmentedSpeedProvider {
        val presentationDurationUs = TimeMapEvaluator.presentationDurationUs(sourceRangeUs, timeMap)
        val boundedStepUs = ceilDivPositive(presentationDurationUs, MAX_SEGMENTS)
        val dynamicStepUs = max(TARGET_PRESENTATION_STEP_US, boundedStepUs)
        val boundaries = buildList {
            add(0L)
            var cursor = dynamicStepUs
            while (cursor < presentationDurationUs) {
                add(cursor)
                if (cursor > Long.MAX_VALUE - dynamicStepUs) break
                cursor += dynamicStepUs
            }
            timeMap.speedPoints.forEach { point ->
                if (point.offsetUs in 1L until presentationDurationUs) add(point.offsetUs)
            }
            add(presentationDurationUs)
        }.distinct().sorted()

        val segments = mutableListOf<SpeedSegment>()
        var lastPresentationUs = 0L
        var lastSourceUs = 0L
        boundaries.drop(1).forEach { presentationUs ->
            val sourceUs = TimeMapEvaluator
                .forwardSourceOffsetAt(timeMap, presentationUs)
                .coerceAtMost(sourceRangeUs)
            if (sourceUs <= lastSourceUs) return@forEach

            val presentationDeltaUs = presentationUs - lastPresentationUs
            val sourceDeltaUs = sourceUs - lastSourceUs
            require(presentationDeltaUs > 0L) { "presentation speed segment must have positive duration" }
            val speed = (sourceDeltaUs.toDouble() / presentationDeltaUs.toDouble()).toFloat()
            require(speed.isFinite() && speed > 0f) { "compiled speed segment must be finite and positive" }
            segments += SpeedSegment(
                sourceStartUs = lastSourceUs,
                sourceEndUs = sourceUs,
                speed = speed,
            )
            lastPresentationUs = presentationUs
            lastSourceUs = sourceUs
        }
        require(lastSourceUs == sourceRangeUs) {
            "compiled speed ramp did not cover the full source range: $lastSourceUs/$sourceRangeUs"
        }
        return SegmentedSpeedProvider(segments)
    }

    private fun ceilDivPositive(value: Long, divisor: Long): Long {
        require(value > 0L) { "value must be positive" }
        require(divisor > 0L) { "divisor must be positive" }
        return value / divisor + if (value % divisor == 0L) 0L else 1L
    }
}
