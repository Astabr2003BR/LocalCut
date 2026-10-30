package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositingSettings

object CropApplyGuard {
    fun canApply(
        capturedClipId: String,
        capturedCompositing: CompositingSettings,
        currentSelectedClipId: String?,
        currentCompositing: CompositingSettings?,
    ): Boolean =
        currentSelectedClipId == capturedClipId &&
            currentCompositing == capturedCompositing
}
