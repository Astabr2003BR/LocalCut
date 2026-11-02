package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackCollisionPolicy

object RippleDeleteUiPolicy {
    fun isAvailable(project: Project, clipId: String): Boolean {
        val track = project.tracks.firstOrNull { candidate ->
            candidate.clips.any { it.id == clipId }
        } ?: return false

        if (track.collisionPolicy != TrackCollisionPolicy.EXCLUSIVE) return false
        if (track.locked) return false

        val layerLocked = project.compositionGraph.nodes.any { node ->
            node.kind == CompositionNodeKind.LAYER &&
                node.clipId == clipId &&
                node.locked
        }
        return !layerLocked
    }
}
