package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind

object FadeTransitionUiPolicy {
    fun createOrUpdate(
        project: Project,
        selectedClipId: String,
        requestedDurationUs: Long,
    ): ClipTransition? {
        if (requestedDurationUs <= 0L) return null
        val boundary = boundary(project, selectedClipId) ?: return null
        val existing = project.transitions.firstOrNull {
            it.fromClipId == boundary.fromClipId && it.toClipId == boundary.toClipId
        }
        if (existing != null && existing.kind != TransitionKind.FADE_TO_BLACK) return null

        val durationUs = minOf(requestedDurationUs, boundary.maxDurationUs)
        if (durationUs <= 0L) return null
        return (existing ?: ClipTransition(
            id = nextId(project, boundary.fromClipId, boundary.toClipId),
            fromClipId = boundary.fromClipId,
            toClipId = boundary.toClipId,
            durationUs = durationUs,
            kind = TransitionKind.FADE_TO_BLACK,
        )).copy(
            durationUs = durationUs,
            kind = TransitionKind.FADE_TO_BLACK,
        )
    }

    fun existingFade(project: Project, selectedClipId: String): ClipTransition? {
        val boundary = boundary(project, selectedClipId) ?: return null
        return project.transitions.firstOrNull {
            it.fromClipId == boundary.fromClipId &&
                it.toClipId == boundary.toClipId &&
                it.kind == TransitionKind.FADE_TO_BLACK
        }
    }

    private data class Boundary(
        val fromClipId: String,
        val toClipId: String,
        val maxDurationUs: Long,
    )

    private fun boundary(project: Project, selectedClipId: String): Boundary? {
        val track = project.tracks.firstOrNull { candidate ->
            candidate.type == TrackType.VISUAL &&
                candidate.collisionPolicy == TrackCollisionPolicy.EXCLUSIVE &&
                candidate.clips.any { it.id == selectedClipId }
        } ?: return null
        val clips = track.clips.sortedBy { it.startUs }
        val index = clips.indexOfFirst { it.id == selectedClipId }
        if (index < 0 || index >= clips.lastIndex) return null
        val from = clips[index]
        val to = clips[index + 1]
        val fromEnd = try {
            Math.addExact(from.startUs, from.durationUs)
        } catch (_: ArithmeticException) {
            return null
        }
        if (fromEnd != to.startUs) return null

        val incomingFadeOnFrom = project.transitions
            .asSequence()
            .filter { it.kind == TransitionKind.FADE_TO_BLACK && it.toClipId == from.id }
            .maxOfOrNull { it.durationUs }
            ?: 0L
        val outgoingFadeOnTo = project.transitions
            .asSequence()
            .filter { it.kind == TransitionKind.FADE_TO_BLACK && it.fromClipId == to.id }
            .maxOfOrNull { it.durationUs }
            ?: 0L
        val availableOnFrom = from.durationUs - incomingFadeOnFrom
        val availableOnTo = to.durationUs - outgoingFadeOnTo
        val maxDurationUs = minOf(availableOnFrom, availableOnTo)
        if (maxDurationUs <= 0L) return null
        return Boundary(from.id, to.id, maxDurationUs)
    }

    private fun nextId(project: Project, fromClipId: String, toClipId: String): String {
        val used = project.transitions.asSequence().map { it.id }.toHashSet()
        val base = "transition-$fromClipId-$toClipId"
        if (base !in used) return base
        var suffix = 2L
        while (true) {
            val candidate = "$base-$suffix"
            if (candidate !in used) return candidate
            check(suffix < Long.MAX_VALUE) { "unable to allocate transition id" }
            suffix += 1L
        }
    }
}
