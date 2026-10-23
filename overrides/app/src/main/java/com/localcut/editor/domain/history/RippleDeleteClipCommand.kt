package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.timeline.TimelineValidator

data class RippleDeleteClipCommand(
    val clipId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
        require(trackIndex >= 0) { "clip $clipId not found" }
        val originalTrack = project.tracks[trackIndex]
        require(originalTrack.collisionPolicy == TrackCollisionPolicy.EXCLUSIVE) {
            "ripple delete requires an exclusive track"
        }
        require(
            project.compositionGraph.nodes.none { node ->
                node.kind == CompositionNodeKind.LAYER && node.clipId == clipId && node.locked
            },
        ) { "clip $clipId is locked" }

        val ordered = originalTrack.clips.sortedWith(compareBy({ it.startUs }, { it.id }))
        val orderedIndex = ordered.indexOfFirst { it.id == clipId }
        require(orderedIndex >= 0) { "clip $clipId not found in ordered track" }
        val removed = ordered[orderedIndex]
        val removedEndUs = Math.addExact(removed.startUs, removed.durationUs)
        val followingIds = ordered
            .drop(orderedIndex + 1)
            .onEach { following ->
                require(following.startUs >= removedEndUs) {
                    "ripple delete cannot close overlapping clips"
                }
            }
            .mapTo(hashSetOf()) { it.id }

        val deleted = DeleteClipWithTransitionsCommand(clipId).apply(project).project
        val deletedTrackIndex = deleted.tracks.indexOfFirst { it.id == originalTrack.id }
        require(deletedTrackIndex >= 0) { "track ${originalTrack.id} not found after delete" }
        val deletedTrack = deleted.tracks[deletedTrackIndex]
        val shiftedTrack = deletedTrack.copy(
            clips = deletedTrack.clips.map { clip ->
                if (clip.id !in followingIds) {
                    clip
                } else {
                    require(clip.startUs >= removed.durationUs) {
                        "ripple delete would create a negative clip start"
                    }
                    clip.copy(startUs = clip.startUs - removed.durationUs)
                }
            },
        )
        val tracks = deleted.tracks.toMutableList().also { it[deletedTrackIndex] = shiftedTrack }
        val edited = deleted.copy(tracks = tracks)
        validateRippleProject(edited, "ripple deleted project invalid")

        return CommandResult(
            project = edited,
            inverse = ReplaceRippleStateCommand(
                trackId = originalTrack.id,
                targetTrack = originalTrack,
                targetTransitions = project.transitions,
            ),
        )
    }
}

private data class ReplaceRippleStateCommand(
    val trackId: String,
    val targetTrack: Track,
    val targetTransitions: List<ClipTransition>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(targetTrack.id == trackId) { "ripple restore track id mismatch" }
        val trackIndex = project.tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "track $trackId not found" }
        val currentTrack = project.tracks[trackIndex]
        val currentTransitions = project.transitions
        val tracks = project.tracks.toMutableList().also { it[trackIndex] = targetTrack }
        val restored = project.copy(
            tracks = tracks,
            transitions = targetTransitions,
        )
        validateRippleProject(restored, "ripple restore project invalid")
        return CommandResult(
            project = restored,
            inverse = ReplaceRippleStateCommand(
                trackId = trackId,
                targetTrack = currentTrack,
                targetTransitions = currentTransitions,
            ),
        )
    }
}

private fun validateRippleProject(project: Project, prefix: String) {
    val issues = TimelineValidator.validate(project)
    require(issues.isEmpty()) { "$prefix: ${issues.first()}" }
}
