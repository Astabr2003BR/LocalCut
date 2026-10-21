package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.timeline.TimelineValidator

data class AddTextOverlayCommand(
    val overlay: TextOverlay,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.textOverlays.none { it.id == overlay.id }) {
            "text overlay ${overlay.id} already exists"
        }
        val edited = validated(project.copy(textOverlays = project.textOverlays + overlay))
        return CommandResult(
            project = edited,
            inverse = DeleteTextOverlayCommand(overlay.id),
        )
    }
}

data class UpdateTextOverlayCommand(
    val overlay: TextOverlay,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.textOverlays.indexOfFirst { it.id == overlay.id }
        require(index >= 0) { "text overlay ${overlay.id} not found" }
        val previous = project.textOverlays[index]
        val overlays = project.textOverlays.toMutableList().also { it[index] = overlay }
        val edited = validated(project.copy(textOverlays = overlays))
        return CommandResult(
            project = edited,
            inverse = UpdateTextOverlayCommand(previous),
        )
    }
}

data class DeleteTextOverlayCommand(
    val overlayId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.textOverlays.indexOfFirst { it.id == overlayId }
        require(index >= 0) { "text overlay $overlayId not found" }
        val removed = project.textOverlays[index]
        val overlays = project.textOverlays.toMutableList().also { it.removeAt(index) }
        val edited = validated(project.copy(textOverlays = overlays))
        return CommandResult(
            project = edited,
            inverse = RestoreTextOverlayCommand(index, removed),
        )
    }
}

private data class RestoreTextOverlayCommand(
    val index: Int,
    val overlay: TextOverlay,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.textOverlays.none { it.id == overlay.id }) {
            "text overlay ${overlay.id} already exists"
        }
        require(index in 0..project.textOverlays.size) { "text overlay restore index out of range" }
        val overlays = project.textOverlays.toMutableList().also { it.add(index, overlay) }
        val edited = validated(project.copy(textOverlays = overlays))
        return CommandResult(
            project = edited,
            inverse = DeleteTextOverlayCommand(overlay.id),
        )
    }
}

private fun validated(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "invalid text overlay edit: ${issues.first()}" }
    return project
}
