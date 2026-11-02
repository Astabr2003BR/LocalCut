package com.localcut.editor.domain.history

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineEditException
import com.localcut.editor.domain.timeline.TimelineErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTrackLockedCommandTest {
    @Test
    fun historyUndoRedoRestoresTrackLockExactly() {
        val history = HistoryManager(project())

        history.execute(SetTrackLockedCommand("visual", true))
        assertTrue(history.project.tracks.single().locked)

        assertTrue(history.undo())
        assertFalse(history.project.tracks.single().locked)

        assertTrue(history.redo())
        assertTrue(history.project.tracks.single().locked)
    }

    @Test
    fun missingTrackFailsWithTrackNotFound() {
        val result = runCatching { SetTrackLockedCommand("missing", true).apply(project()) }
        val error = result.exceptionOrNull() as TimelineEditException

        assertEquals(TimelineErrorCode.TRACK_NOT_FOUND, error.code)
    }

    private fun project(): Project = Project(
        id = "track-lock-command",
        schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
        title = "Track lock",
        width = 1080,
        height = 1920,
        fps = 30,
        assets = emptyList(),
        tracks = listOf(
            Track(
                id = "visual",
                type = TrackType.VISUAL,
                order = 0,
            ),
        ),
    )
}
