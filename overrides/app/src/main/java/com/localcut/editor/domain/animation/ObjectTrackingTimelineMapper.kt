package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.time.TimeMapEvaluator

data class ObjectTrackingSourceSample(
    val sourceOffsetUs: Long,
    val dxNorm: Double,
    val dyNorm: Double,
    val confidence: Double,
) {
    init {
        require(sourceOffsetUs > 0L) { "OBJECT_TRACKING_SOURCE_SAMPLE_TIME_INVALID" }
        require(dxNorm.isFinite() && dyNorm.isFinite() && confidence.isFinite()) {
            "OBJECT_TRACKING_SOURCE_SAMPLE_NON_FINITE"
        }
        require(confidence in 0.0..1.0) { "OBJECT_TRACKING_SOURCE_SAMPLE_CONFIDENCE_INVALID" }
    }
}

object ObjectTrackingTimelineMapper {
    fun toPresentationSamples(
        samples: List<ObjectTrackingSourceSample>,
        sourceRangeUs: Long,
        timeMap: TimeMap,
    ): List<ObjectTrackingPositionSample> {
        require(sourceRangeUs > 0L) { "OBJECT_TRACKING_SOURCE_RANGE_INVALID" }
        require(samples.isNotEmpty()) { "OBJECT_TRACKING_SOURCE_SAMPLES_EMPTY" }
        require(timeMap.validationIssues().isEmpty()) { "OBJECT_TRACKING_TIME_MAP_INVALID" }
        require(!timeMap.reverse) { "OBJECT_TRACKING_REVERSE_UNSUPPORTED" }
        require(timeMap.freezeSegments.isEmpty()) { "OBJECT_TRACKING_FREEZE_UNSUPPORTED" }
        require(samples.zipWithNext().all { (left, right) -> right.sourceOffsetUs > left.sourceOffsetUs }) {
            "OBJECT_TRACKING_SOURCE_SAMPLES_UNORDERED"
        }
        require(samples.all { it.sourceOffsetUs <= sourceRangeUs }) {
            "OBJECT_TRACKING_SOURCE_SAMPLE_OUT_OF_RANGE"
        }

        val presentationDurationUs = TimeMapEvaluator.presentationDurationUs(sourceRangeUs, timeMap)
        val mapped = samples.map { sample ->
            ObjectTrackingPositionSample(
                offsetUs = presentationOffsetForSource(
                    sourceOffsetUs = sample.sourceOffsetUs,
                    sourceRangeUs = sourceRangeUs,
                    presentationDurationUs = presentationDurationUs,
                    timeMap = timeMap,
                ),
                dxNorm = sample.dxNorm,
                dyNorm = sample.dyNorm,
                confidence = sample.confidence,
            )
        }
        require(mapped.first().offsetUs > 0L) { "OBJECT_TRACKING_PRESENTATION_SAMPLE_TIME_INVALID" }
        require(mapped.zipWithNext().all { (left, right) -> right.offsetUs > left.offsetUs }) {
            "OBJECT_TRACKING_PRESENTATION_SAMPLES_COLLAPSED"
        }
        return mapped
    }

    private fun presentationOffsetForSource(
        sourceOffsetUs: Long,
        sourceRangeUs: Long,
        presentationDurationUs: Long,
        timeMap: TimeMap,
    ): Long {
        var low = 0L
        var high = presentationDurationUs
        while (low < high) {
            val mid = low + (high - low) / 2L
            val mappedSourceUs = TimeMapEvaluator.sourceOffsetAt(
                timeMap = timeMap,
                presentationOffsetUs = mid,
                sourceRangeUs = sourceRangeUs,
            )
            if (mappedSourceUs >= sourceOffsetUs) {
                high = mid
            } else {
                low = mid + 1L
            }
        }
        require(low in 1L..presentationDurationUs) { "OBJECT_TRACKING_PRESENTATION_SAMPLE_TIME_INVALID" }
        return low
    }
}
