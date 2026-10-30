package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.TransformValue

data class ObjectTrackingApplySnapshot(
    val clipId: String,
    val assetId: String,
    val startUs: Long,
    val durationUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val transform: TransformValue,
    val propertyAnimations: List<PropertyAnimation>,
    val compositing: CompositingSettings,
    val timeMap: TimeMap,
)

object ObjectTrackingApplyGuard {
    fun capture(clip: Clip): ObjectTrackingApplySnapshot = ObjectTrackingApplySnapshot(
        clipId = clip.id,
        assetId = clip.assetId,
        startUs = clip.startUs,
        durationUs = clip.durationUs,
        sourceInUs = clip.sourceInUs,
        sourceOutUs = clip.sourceOutUs,
        transform = clip.transform,
        propertyAnimations = clip.propertyAnimations.toList(),
        compositing = clip.compositing,
        timeMap = clip.timeMap,
    )

    fun canApply(
        captured: ObjectTrackingApplySnapshot,
        currentClip: Clip?,
    ): Boolean = currentClip != null && capture(currentClip) == captured
}
