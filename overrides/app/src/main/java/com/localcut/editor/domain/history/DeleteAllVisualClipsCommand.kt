package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineValidator

object DeleteAllVisualClipsCommand : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val removedClipIds = project.tracks
            .asSequence()
            .filter { it.type == TrackType.VISUAL }
            .flatMap { it.clips.asSequence() }
            .map { it.id }
            .toSet()
        require(removedClipIds.isNotEmpty()) { "no visual clips" }

        val remainingNodes = project.compositionGraph.nodes.filterNot { node ->
            node.kind == CompositionNodeKind.LAYER && node.clipId?.let { it in removedClipIds } == true
        }
        val remainingNodeIds = remainingNodes.map { it.id }.toSet()
        val cleanedNodes = remainingNodes.map { node ->
            if (node.parentId != null && node.parentId !in remainingNodeIds) {
                node.copy(parentId = null)
            } else {
                node
            }
        }

        val edited = project.copy(
            tracks = project.tracks.map { track ->
                if (track.type == TrackType.VISUAL) track.copy(clips = emptyList()) else track
            },
            transitions = project.transitions.filterNot { transition ->
                transition.fromClipId in removedClipIds || transition.toClipId in removedClipIds
            },
            compositionGraph = project.compositionGraph.copy(nodes = cleanedNodes),
            designElements = project.designElements.map { element ->
                if (element.clipId?.let { it in removedClipIds } == true) element.copy(clipId = null) else element
            },
        )
        val issues = TimelineValidator.validate(edited)
        require(issues.isEmpty()) { "bulk visual delete invalid: ${issues.first()}" }
        return CommandResult(
            project = edited,
            inverse = RestoreProjectAfterVisualDeleteCommand(project),
        )
    }
}

private data class RestoreProjectAfterVisualDeleteCommand(
    val snapshot: Project,
) : EditorCommand {
    override fun apply(project: Project): CommandResult {
        val issues = TimelineValidator.validate(snapshot)
        require(issues.isEmpty()) { "restored project invalid: ${issues.first()}" }
        return CommandResult(
            project = snapshot,
            inverse = DeleteAllVisualClipsCommand,
        )
    }
}
