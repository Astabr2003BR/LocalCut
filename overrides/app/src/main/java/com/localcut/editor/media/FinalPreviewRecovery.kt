package com.localcut.editor.media

import com.localcut.editor.domain.model.Project

object FinalPreviewRecovery {
    fun restore(
        controller: PreviewController,
        project: Project,
        positionUs: Long,
        durationUs: Long,
    ): Result<Unit> {
        val safeDurationUs = durationUs.coerceAtLeast(0L)
        val targetUs = positionUs.coerceIn(0L, safeDurationUs)
        return controller.load(project).onSuccess {
            controller.seekToUs(targetUs)
        }
    }
}
