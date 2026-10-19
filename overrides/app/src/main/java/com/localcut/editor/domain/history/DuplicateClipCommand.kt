package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.timeline.TimelineEditException
import com.localcut.editor.domain.timeline.TimelineErrorCode
import com.localcut.editor.domain.timeline.TimelineOps
import com.localcut.editor.domain.timeline.TimelineValidator
import com.localcut.editor.domain.timeline.duplicateClip

data class DuplicateClipCommand(
    val clipId: String,
    val duplicateClipId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
        if (trackIndex < 0) {
            throw TimelineEditException(TimelineErrorCode.CLIP_NOT_FOUND, "clip $clipId not found")
        }
        val originalTrack = project.tracks[trackIndex]
        return CommandResult(
            project = TimelineOps.duplicateClip(project, clipId, duplicateClipId),
            inverse = ReplaceTrackCommand(originalTrack.id, originalTrack),
        )
    }
}

private data class ReplaceTrackCommand(
    val trackId: String,
    val replacement: Track,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val trackIndex = project.tracks.indexOfFirst { it.id == trackId }
        if (trackIndex < 0) {
            throw TimelineEditException(TimelineErrorCode.TRACK_NOT_FOUND, "track $trackId not found")
        }
        val current = project.tracks[trackIndex]
        val tracks = project.tracks.toMutableList().also { it[trackIndex] = replacement }
        val restored = project.copy(tracks = tracks)
        val issues = TimelineValidator.validate(restored)
        if (issues.isNotEmpty()) {
            throw TimelineEditException(
                TimelineErrorCode.INVALID_PROJECT,
                "replacement track invalid: ${issues.first()}",
            )
        }
        return CommandResult(
            project = restored,
            inverse = ReplaceTrackCommand(trackId, current),
        )
    }
}
