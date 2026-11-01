package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineEditException
import com.localcut.editor.domain.timeline.TimelineErrorCode
import com.localcut.editor.domain.timeline.TimelineValidator

data class SetTrackLockedCommand(
    val trackId: String,
    val locked: Boolean,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.tracks.indexOfFirst { it.id == trackId }
        if (index < 0) {
            throw TimelineEditException(TimelineErrorCode.TRACK_NOT_FOUND, "track $trackId not found")
        }
        val old = project.tracks[index]
        if (old.locked == locked) {
            return CommandResult(project, SetTrackLockedCommand(trackId, old.locked))
        }
        val tracks = project.tracks.toMutableList().also { it[index] = old.copy(locked = locked) }
        val updated = project.copy(tracks = tracks)
        val issues = TimelineValidator.validate(updated)
        if (issues.isNotEmpty()) {
            throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "invalid project: ${issues.first()}")
        }
        return CommandResult(updated, SetTrackLockedCommand(trackId, old.locked))
    }
}
