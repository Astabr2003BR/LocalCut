package com.localcut.editor.media

import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue

object KeyframeRenderResolver {
    fun valueAt(
        transform: TransformValue,
        keyframes: List<TransformKeyframe>,
        presentationTimeUs: Long,
        presentationStartUs: Long,
    ): TransformValue {
        if (keyframes.isEmpty()) return transform
        val localTimeUs = if (presentationTimeUs <= presentationStartUs) {
            0L
        } else {
            presentationTimeUs - presentationStartUs
        }
        return TransformInterpolator.valueAt(keyframes, localTimeUs)
    }

    fun effectiveOpacity(clipOpacity: Double, value: TransformValue): Double =
        (clipOpacity * value.opacity).coerceIn(0.0, 1.0)
}
