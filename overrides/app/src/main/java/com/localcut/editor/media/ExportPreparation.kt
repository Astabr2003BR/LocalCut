package com.localcut.editor.media

import com.localcut.editor.domain.model.Project

object ExportPreparation {
    fun prepare(
        project: Project,
        requestedProfile: ExportProfile,
        capabilities: ExportCapabilities,
    ): Result<Media3ExportSettings> = runCatching {
        ExportRequestValidator.validate(project, requestedProfile).getOrThrow()
        Media3ExportSettingsResolver.resolve(requestedProfile, capabilities).getOrThrow()
    }
}
