package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineOps
import com.localcut.editor.domain.timeline.TimelineValidator

data class DeleteClipWithTransitionsCommand(
    val clipId: String,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val trackIndex = project.tracks.indexOfFirst { track -> track.clips.any { it.id == clipId } }
        require(trackIndex >= 0) { "clip $clipId not found" }
        val track = project.tracks[trackIndex]
        val clipIndex = track.clips.indexOfFirst { it.id == clipId }
        val clip = track.clips[clipIndex]
        val attachedTransitions = project.transitions
            .mapIndexedNotNull { index, transition ->
                if (transition.fromClipId == clipId || transition.toClipId == clipId) {
                    IndexedTransition(index, transition)
                } else {
                    null
                }
            }
        val withoutAttachedTransitions = project.copy(
            transitions = project.transitions.filterNot { transition ->
                transition.fromClipId == clipId || transition.toClipId == clipId
            },
        )
        val deleted = TimelineOps.deleteClip(withoutAttachedTransitions, clipId)
        return CommandResult(
            project = deleted,
            inverse = RestoreClipWithTransitionsCommand(
                trackId = track.id,
                clipIndex = clipIndex,
                clip = clip,
                transitions = attachedTransitions,
            ),
        )
    }
}

private data class IndexedTransition(
    val index: Int,
    val transition: ClipTransition,
)

private data class RestoreClipWithTransitionsCommand(
    val trackId: String,
    val clipIndex: Int,
    val clip: Clip,
    val transitions: List<IndexedTransition>,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        require(project.findClip(clip.id) == null) { "clip ${clip.id} already exists" }
        val trackIndex = project.tracks.indexOfFirst { it.id == trackId }
        require(trackIndex >= 0) { "track $trackId not found" }
        val track = project.tracks[trackIndex]
        require(clipIndex in 0..track.clips.size) { "clip restore index out of range" }

        val clips = track.clips.toMutableList().also { it.add(clipIndex, clip) }
        val tracks = project.tracks.toMutableList().also {
            it[trackIndex] = track.copy(clips = clips)
        }
        val restoredTransitions = project.transitions.toMutableList()
        transitions.sortedBy { it.index }.forEach { indexed ->
            require(indexed.index in 0..restoredTransitions.size) {
                "transition restore index out of range"
            }
            require(restoredTransitions.none { it.id == indexed.transition.id }) {
                "transition ${indexed.transition.id} already exists"
            }
            restoredTransitions.add(indexed.index, indexed.transition)
        }
        val restored = project.copy(
            tracks = tracks,
            transitions = restoredTransitions,
        )
        val issues = TimelineValidator.validate(restored)
        require(issues.isEmpty()) { "restored project invalid: ${issues.first()}" }
        return CommandResult(
            project = restored,
            inverse = DeleteClipWithTransitionsCommand(clip.id),
        )
    }
}
