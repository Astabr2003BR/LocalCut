package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.session.ProjectSessionState

/** UI eligibility/confirmation only; RippleDeleteClipCommand revalidates atomically on apply. */
data class RippleDeletePreview(
    val clipId: String,
    val trackId: String,
    val removedDurationUs: Long,
    val followingClipCount: Int,
)

object RippleDeleteUiPolicy {
    fun preview(project: Project, clipId: String): RippleDeletePreview? {
        if (clipId.isBlank()) return null
        val track = project.tracks.singleOrNull { it.clips.any { clip -> clip.id == clipId } }
            ?: return null
        if (track.collisionPolicy != TrackCollisionPolicy.EXCLUSIVE || track.locked) return null
        if (project.compositionGraph.nodes.any {
            it.kind == CompositionNodeKind.LAYER && it.clipId == clipId && it.locked
        }) return null
        val clip = track.clips.singleOrNull { it.id == clipId } ?: return null
        val end = runCatching { Math.addExact(clip.startUs, clip.durationUs) }.getOrNull()
            ?: return null
        if (clip.startUs < 0L || clip.durationUs <= 0L) return null
        // The backend rejects overlaps too; hide a control known to be invalid.
        if (track.clips.any { it.id != clipId && it.startUs > clip.startUs && it.startUs < end }) return null
        val following = track.clips.count { it.id != clipId && it.startUs >= end }
        return RippleDeletePreview(
            clipId = clipId,
            trackId = track.id,
            removedDurationUs = clip.durationUs,
            followingClipCount = following,
        )
    }

    fun matchesCurrent(
        state: ProjectSessionState,
        expectedRevision: Long,
        preview: RippleDeletePreview,
    ): Boolean =
        state.revision == expectedRevision &&
            state.selectedClipId == preview.clipId &&
            preview(state.project, preview.clipId) == preview
}
