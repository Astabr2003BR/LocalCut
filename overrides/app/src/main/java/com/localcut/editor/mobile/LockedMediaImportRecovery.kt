package com.localcut.editor.mobile

import com.localcut.editor.domain.history.SetTrackLockedCommand
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSession

/**
 * Handles a failed media insert without bypassing track locks.
 *
 * A recovery is offered only when the exact target track is still locked.
 * The explicit user's confirmation is required before calling unlock().
 */
object LockedMediaImportRecovery {
    /**
     * Preflight lock detection for direct media links with a recognizable extension.
     * This is only a UI hint: the downloaded bytes still undergo full MIME/content
     * validation and the track lock is never bypassed without user confirmation.
     * Unknown URLs continue to use blockedTrackId() after the import attempt.
     */
    fun blockedTrackForMediaLink(project: Project, rawUrl: String): String? {
        val uri = RemoteMediaUrlPolicy.parseAndValidate(rawUrl).getOrNull() ?: return null
        val path = uri.path?.lowercase().orEmpty()
        val type = when {
            listOf(".mp3", ".m4a", ".wav", ".aac", ".ogg", ".flac").any { ext -> path.endsWith(ext) } ->
                TrackType.AUDIO
            listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".mp4", ".mov", ".mkv", ".webm").any { ext -> path.endsWith(ext) } ->
                TrackType.VISUAL
            else -> return null
        }
        return project.tracks.firstOrNull { it.type == type && it.locked }?.id
    }

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
