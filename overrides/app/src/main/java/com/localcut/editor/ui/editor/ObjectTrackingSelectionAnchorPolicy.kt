package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Clip

object ObjectTrackingSelectionAnchorPolicy {
    fun anchorProjectTimeUs(clip: Clip): Long = clip.startUs

    fun canCapture(
        playheadUs: Long,
        clip: Clip,
        fps: Int,
    ): Boolean {
        require(fps > 0) { "OBJECT_TRACKING_FPS_INVALID" }
        if (playheadUs < clip.startUs) return false
        val frameDurationUs = 1_000_000L / fps.toLong()
        return playheadUs - clip.startUs <= frameDurationUs
    }
}
