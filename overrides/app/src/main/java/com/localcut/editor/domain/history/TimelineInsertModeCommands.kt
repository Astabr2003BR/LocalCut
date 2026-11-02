package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.timeline.TimelineEditException
import com.localcut.editor.domain.timeline.TimelineErrorCode
import com.localcut.editor.domain.timeline.TimelineValidator

/**
 * Inserts [clip] into an EXCLUSIVE track and shifts clips that start at or after
 * [clip.startUs] by exactly [clip.durationUs].
 *
 * To keep the operation deterministic and non-destructive, insertion inside an
 * existing clip is rejected instead of implicitly splitting source media.
 */
data class InsertClipAtTimelineCommand(
    val trackId: String,
    val clip: Clip,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(clip.startUs >= 0L) { "insert start must be >= 0" }
        require(clip.durationUs > 0L) { "insert duration must be > 0" }
        require(project.findClip(clip.id) == null) { "clip ${clip.id} already exists" }

        val trackIndex = project.tracks.indexOfFirst { it.id == trackId }
        if (trackIndex < 0) {
            throw TimelineEditException(TimelineErrorCode.TRACK_NOT_FOUND, "track $trackId not found")
        }
        val track = project.tracks[trackIndex]
        require(!track.locked) { "track $trackId is locked" }
        require(track.collisionPolicy == TrackCollisionPolicy.EXCLUSIVE) {
            "insert mode requires an exclusive track"
        }
        require(project.assets.any { it.id == clip.assetId }) {
            "asset ${clip.assetId} not found"
        }

        val insertionUs = clip.startUs
        val containing = track.clips.firstOrNull { existing ->
            insertionUs > existing.startUs && insertionUs < Math.addExact(existing.startUs, existing.durationUs)
        }
        require(containing == null) {
            "insert point falls inside clip ${containing?.id}"
        }

        val shifted = track.clips.map { existing ->
            if (existing.startUs < insertionUs) {
                existing
            } else {
                existing.copy(startUs = Math.addExact(existing.startUs, clip.durationUs))
            }
        }
        val editedTrack = track.copy(clips = shifted + clip)
        val tracks = project.tracks.toMutableList().also { it[trackIndex] = editedTrack }
        val edited = project.copy(tracks = tracks)
        validateInsertProject(edited)

        return CommandResult(
            project = edited,
            inverse = ReplaceInsertedTrackStateCommand(trackId, track),
        )
    }
}

private data class ReplaceInsertedTrackStateCommand(
    val trackId: String,
    val targetTrack: Track,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val index = project.tracks.indexOfFirst { it.id == trackId }
        require(index >= 0) { "track $trackId not found" }
        val current = project.tracks[index]
        val tracks = project.tracks.toMutableList().also { it[index] = targetTrack }
        val restored = project.copy(tracks = tracks)
        validateInsertProject(restored)
        return CommandResult(
            project = restored,
            inverse = ReplaceInsertedTrackStateCommand(trackId, current),
        )
    }
}

private fun validateInsertProject(project: Project) {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "insert edited project invalid: ${issues.first()}" }
}
