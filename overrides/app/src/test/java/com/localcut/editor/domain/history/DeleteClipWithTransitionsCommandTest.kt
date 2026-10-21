package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteClipWithTransitionsCommandTest {
    @Test
    fun deleteRemovesAttachedTransitionAndUndoRestoresExactProject() {
        val initial = projectWithTransition()
        val history = HistoryManager(initial)

        history.execute(DeleteClipWithTransitionsCommand("a"))

        assertNull(history.project.findClip("a"))
        assertTrue(history.project.transitions.isEmpty())

        assertTrue(history.undo())
        assertEquals(initial, history.project)

        assertTrue(history.redo())
        assertNull(history.project.findClip("a"))
        assertTrue(history.project.transitions.isEmpty())
    }

    private fun projectWithTransition(): Project {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 6_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        return Project.create("Delete transition", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip("a", asset.id, 0L, 3_000_000L, 0L, 3_000_000L),
                        Clip("b", asset.id, 3_000_000L, 3_000_000L, 3_000_000L, 6_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            transitions = listOf(
                ClipTransition("ab", "a", "b", 500_000L, TransitionKind.FADE_TO_BLACK),
            ),
        )
    }
}
