package com.localcut.editor.ui.editor

import com.localcut.editor.domain.animation.ObjectTrackingPathPlanner
import com.localcut.editor.domain.animation.ObjectTrackingSourceSample
import com.localcut.editor.domain.animation.ObjectTrackingTimelineMapper
import com.localcut.editor.domain.history.SetClipPropertyAnimationsCommand
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.termux.TermuxObjectTrackingAnalysis

object ObjectTrackingApplyCommandFactory {
    fun createFromAnalysis(
        captured: ObjectTrackingApplySnapshot,
        currentClip: Clip,
        analysis: TermuxObjectTrackingAnalysis,
        projectWidth: Int,
        projectHeight: Int,
        minConfidence: Double = 0.35,
    ): SetClipPropertyAnimationsCommand {
        require(ObjectTrackingApplyGuard.canApply(captured, currentClip)) {
            "OBJECT_TRACKING_RESULT_STALE"
        }
        require(projectWidth > 0 && projectHeight > 0) { "OBJECT_TRACKING_PROJECT_SIZE_INVALID" }
        require(
            analysis.sourceInUs == currentClip.sourceInUs &&
                analysis.sourceOutUs == currentClip.sourceOutUs
        ) { "OBJECT_TRACKING_SOURCE_RANGE_MISMATCH" }
        val sourceRangeUs = currentClip.sourceOutUs - currentClip.sourceInUs
        require(sourceRangeUs > 0L) { "OBJECT_TRACKING_SOURCE_RANGE_INVALID" }

        val presentationSamples = ObjectTrackingTimelineMapper.toPresentationSamples(
            samples = analysis.samples.map { sample ->
                ObjectTrackingSourceSample(
                    sourceOffsetUs = sample.timeUs,
                    dxNorm = sample.dxNorm,
                    dyNorm = sample.dyNorm,
                    confidence = sample.confidence,
                )
            },
            sourceRangeUs = sourceRangeUs,
            timeMap = currentClip.timeMap,
        )
        val trackingAnimations = ObjectTrackingPathPlanner.planFromSourceGeometry(
            samples = presentationSamples,
            sourceWidthPx = analysis.frameWidth,
            sourceHeightPx = analysis.frameHeight,
            outputWidthPx = projectWidth,
            outputHeightPx = projectHeight,
            transformSpaceWidthPx = projectWidth.toDouble(),
            transformSpaceHeightPx = projectHeight.toDouble(),
            baseX = currentClip.transform.x,
            baseY = currentClip.transform.y,
            minConfidence = minConfidence,
        )
        return create(
            captured = captured,
            currentClip = currentClip,
            trackingAnimations = trackingAnimations,
        )
    }

    fun create(
        captured: ObjectTrackingApplySnapshot,
        currentClip: Clip,
        trackingAnimations: List<PropertyAnimation>,
    ): SetClipPropertyAnimationsCommand {
        require(ObjectTrackingApplyGuard.canApply(captured, currentClip)) {
            "OBJECT_TRACKING_RESULT_STALE"
        }
        val merged = ObjectTrackingUiPolicy.merge(
            existing = currentClip.propertyAnimations,
            tracking = trackingAnimations,
        )
        return SetClipPropertyAnimationsCommand(
            clipId = currentClip.id,
            propertyAnimations = merged,
        )
    }
}
