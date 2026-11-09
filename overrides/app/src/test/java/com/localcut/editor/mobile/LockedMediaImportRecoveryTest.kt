package com.localcut.editor.mobile

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.history.HistoryManager
import com.localcut.editor.domain.history.SetTrackLockedCommand
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedMediaImportRecoveryTest {
    private fun project(audioLocked: Boolean = true, visualLocked: Boolean = false) = Project(
        id = "recovery",
        schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
        title = "Recovery",
        width = 864,
        height = 1536,
        fps = 30,
        assets = emptyList(),
        tracks = listOf(
            Track(id = "visual-1", type = TrackType.VISUAL, order = 0, locked = visualLocked),
            Track(id = "audio-1", type = TrackType.AUDIO, order = 1, locked = audioLocked),
        ),
    )

    @Test fun recognizesAudioLockOnlyWhenActive() {
        assertEquals(
            "audio-1",
            LockedMediaImportRecovery.blockedTrackId(
                project(),
                IllegalArgumentException("track locked: audio-1"),
            ),
        )
        assertNull(LockedMediaImportRecovery.blockedTrackId(
            project(audioLocked = false),
            IllegalArgumentException("track locked: audio-1"),
        ))
    }

    @Test fun recognizesVisualLockWithoutAffectingAudio() {
        assertEquals(
            "visual-1",
            LockedMediaImportRecovery.blockedTrackId(
                project(visualLocked = true),
                IllegalArgumentException("track locked: visual-1"),
            ),
        )
    }

    @Test fun cannotUnlockUsingUnrelatedOrForgedErrorMessages() {
        val project = project()
        for (message in listOf("track locked: missing", "track locked: audio-1 extra", 
                "track locked: visual-1", "network timeout", "TRACK_LOCKED", "")) {
            assertNull(LockedMediaImportRecovery.blockedTrackId(project, IllegalStateException(message)))
        }
        assertNull(LockedMediaImportRecovery.blockedTrackId(project, null))
    }

    @Test fun unlockedTrackCanAcceptChangesAndUndoRestoresLock() {
        val history = HistoryManager(project())
        val audio = history.project.tracks.single { it.id == "audio-1" }
        assertTrue(audio.locked)
        history.execute(SetTrackLockedCommand(audio.id, false))
        assertFalse(history.project.tracks.single { it.id == "audio-1" }.locked)
        assertFalse(history.project.tracks.single { it.id == "visual-1" }.locked)
        assertTrue(history.undo())
        assertTrue(history.project.tracks.single { it.id == "audio-1" }.locked)
        assertTrue(history.redo())
        assertFalse(history.project.tracks.single { it.id == "audio-1" }.locked)
    }
}
