package com.localcut.editor.media

import com.localcut.editor.domain.model.Project

data class PreviewRenderSelection(
    val qualityTier: PreviewQualityTier,
    val outputSettings: RenderOutputSettings,
)

object PreviewRenderSettingsResolver {
    fun resolve(
        project: Project,
        runtime: PreviewRuntimeConditions,
    ): PreviewRenderSelection {
        val tier = AdaptivePreviewPolicy.choose(project, runtime)
        return PreviewRenderSelection(
            qualityTier = tier,
            outputSettings = RenderOutputSettings.forPreview(tier),
        )
    }
}
