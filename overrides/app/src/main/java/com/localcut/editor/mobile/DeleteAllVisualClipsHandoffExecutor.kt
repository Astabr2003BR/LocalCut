package com.localcut.editor.mobile

import com.localcut.editor.domain.history.DeleteAllVisualClipsCommand
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ProjectSession

object DeleteAllVisualClipsHandoffExecutor {
    fun execute(session: ProjectSession): Result<Boolean> = runCatching {
        val hasVisualClips = session.state.project.tracks.any { track ->
            track.type == TrackType.VISUAL && track.clips.isNotEmpty()
        }
        if (!hasVisualClips) return@runCatching false

        val beforeRevision = session.state.revision
        val selectedClipId = session.state.selectedClipId
        session.dispatch(EditorAction.Execute(DeleteAllVisualClipsCommand))
        if (session.state.revision == beforeRevision) {
            error(session.state.lastError ?: "bulk visual delete was not applied")
        }
        if (selectedClipId != null && session.state.project.findClip(selectedClipId) == null) {
            session.dispatch(EditorAction.SelectClip(null))
        }
        true
    }
}
