package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineEditException
import com.localcut.editor.domain.timeline.TimelineErrorCode
import com.localcut.editor.domain.timeline.TimelineOps
import com.localcut.editor.domain.timeline.TimelineValidator

data class SetAudioFadesCommand(
    val clipId: String,
    val fadeInUs: Long,
    val fadeOutUs: Long,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val old = project.findClip(clipId)
            ?: throw TimelineEditException(TimelineErrorCode.CLIP_NOT_FOUND, "clip $clipId not found")
        return CommandResult(
            project = TimelineOps.setAudioFades(project, clipId, fadeInUs, fadeOutUs),
            inverse = SetAudioFadesCommand(clipId, old.fadeInUs, old.fadeOutUs),
        )
    }
}

data class SetTrackMutedCommand(
    val trackId: String,
    val muted: Boolean,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.tracks.indexOfFirst { it.id == trackId }
        if (index < 0) throw TimelineEditException(TimelineErrorCode.TRACK_NOT_FOUND, "track $trackId not found")
        val old = project.tracks[index]
        val tracks = project.tracks.toMutableList().also { it[index] = old.copy(muted = muted) }
        val updated = validated(project.copy(tracks = tracks))
        return CommandResult(updated, SetTrackMutedCommand(trackId, old.muted))
    }
}

data class SetTrackSoloCommand(
    val trackId: String,
    val solo: Boolean,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.tracks.indexOfFirst { it.id == trackId }
        if (index < 0) throw TimelineEditException(TimelineErrorCode.TRACK_NOT_FOUND, "track $trackId not found")
        val old = project.tracks[index]
        if (old.type != TrackType.AUDIO) {
            throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "solo is only supported for audio tracks")
        }
        val tracks = project.tracks.toMutableList().also { it[index] = old.copy(solo = solo) }
        val updated = validated(project.copy(tracks = tracks))
        return CommandResult(updated, SetTrackSoloCommand(trackId, old.solo))
    }
}

private fun validated(project: Project): Project {
    val issues = TimelineValidator.validate(project)
    if (issues.isNotEmpty()) {
        throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "invalid project: ${issues.first()}")
    }
    return project
}
