package com.localcut.editor.ui.editor

import com.localcut.editor.domain.history.SetClipPropertyAnimationsCommand
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.PropertyAnimation

object ObjectTrackingApplyCommandFactory {
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
