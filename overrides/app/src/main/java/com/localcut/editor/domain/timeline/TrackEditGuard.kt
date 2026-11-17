package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track

internal object TrackEditGuard {
    fun requireUnlocked(track: Track) {
        if (track.locked) {
            throw TimelineEditException(
                TimelineErrorCode.TRACK_LOCKED,
                "track locked: ${track.id}",
            )
        }
    }

    fun requireClipTrackUnlocked(project: Project, clipId: String): Track {
        val track = project.tracks.firstOrNull { candidate ->
            candidate.clips.any { clip -> clip.id == clipId }
        } ?: throw TimelineEditException(
            TimelineErrorCode.CLIP_NOT_FOUND,
            "clip $clipId not found",
        )
        requireUnlocked(track)
        val nodes = project.compositionGraph.nodes.associateBy { it.id }
        project.compositionGraph.nodes
            .filter { it.kind == CompositionNodeKind.LAYER && it.clipId == clipId }
            .forEach { layer ->
                var current: CompositionNode? = layer
                val visited = mutableSetOf<String>()
                while (current != null) {
                    val node = current
                    if (!visited.add(node.id)) {
                        throw TimelineEditException(TimelineErrorCode.INVALID_PROJECT, "invalid composition hierarchy")
                    }
                    if (node.locked) {
                        throw TimelineEditException(TimelineErrorCode.TRACK_LOCKED, "clip locked: $clipId")
                    }
                    current = node.parentId?.let { parentId ->
                        nodes[parentId] ?: throw TimelineEditException(
                            TimelineErrorCode.INVALID_PROJECT,
                            "invalid composition parent: $parentId",
                        )
                    }
                }
            }
        return track
    }
}
