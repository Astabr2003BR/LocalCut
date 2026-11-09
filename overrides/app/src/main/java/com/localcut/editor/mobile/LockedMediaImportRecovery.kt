package com.localcut.editor.mobile

import com.localcut.editor.domain.history.SetTrackLockedCommand
import com.localcut.editor.domain.model.Project
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSession

/**
 * Handles a failed media insert without bypassing track locks.
 *
 * A recovery is offered only when the exact target track is still locked.
 * The explicit user's confirmation is required before calling unlock().
 */
object LockedMediaImportRecovery {
    fun blockedTrackId(project: Project, failure: Throwable?): String? {
        val message = failure?.message ?: return null
        return project.tracks.firstOrNull { it.locked && message == "track locked: ${it.id}" }?.id
    }

    fun unlock(session: ProjectSession, trackId: String): Boolean {
        val track = session.state.project.tracks.firstOrNull { it.id == trackId } ?: return false
        if (track.locked) {
            session.dispatch(EditorAction.Execute(SetTrackLockedCommand(trackId, false)))
        }
        return session.state.project.tracks.firstOrNull { it.id == trackId }?.locked == false
    }
}
