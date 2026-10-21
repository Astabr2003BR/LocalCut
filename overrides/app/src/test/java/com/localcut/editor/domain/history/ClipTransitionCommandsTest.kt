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
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTransitionCommandsTest {
    @Test
    fun addTransitionIsUndoableAndRedoable() {
        val initial = sequentialProject()
        val history = HistoryManager(initial)
        val transition = transition(500_000L)

        history.execute(AddClipTransitionCommand(transition))
        assertEquals(listOf(transition), history.project.transitions)
        assertTrue(history.canUndo)

        assertTrue(history.undo())
        assertEquals(initial, history.project)
        assertTrue(history.canRedo)

        assertTrue(history.redo())
        assertEquals(listOf(transition), history.project.transitions)
    }

    @Test
    fun updateTransitionRestoresPreviousValueOnUndo() {
        val original = transition(300_000L)
        val initial = sequentialProject().copy(transitions = listOf(original))
        val history = HistoryManager(initial)
        val updated = original.copy(durationUs = 700_000L)

        history.execute(UpdateClipTransitionCommand(updated))
        assertEquals(updated, history.project.transitions.single())

        assertTrue(history.undo())
        assertEquals(original, history.project.transitions.single())
    }

    @Test
    fun deleteTransitionRestoresOriginalIndexOnUndo() {
        val first = transition(300_000L).copy(id = "t-ab")
        val secondProject = threeClipProject().copy(
            transitions = listOf(
                first,
                ClipTransition("t-bc", "b", "c", 400_000L, TransitionKind.FADE_TO_BLACK),
            ),
        )
        val history = HistoryManager(secondProject)

        history.execute(DeleteClipTransitionCommand("t-ab"))
        assertEquals(listOf("t-bc"), history.project.transitions.map { it.id })

        assertTrue(history.undo())
        assertEquals(listOf("t-ab", "t-bc"), history.project.transitions.map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun addRejectsDuplicateTransitionId() {
        val existing = transition(500_000L)
        AddClipTransitionCommand(existing).apply(
            sequentialProject().copy(transitions = listOf(existing)),
        )
    }

    private fun transition(durationUs: Long) = ClipTransition(
        id = "t-ab",
        fromClipId = "a",
        toClipId = "b",
        durationUs = durationUs,
        kind = TransitionKind.FADE_TO_BLACK,
    )

    private fun sequentialProject(): Project {
        val asset = videoAsset()
        return Project.create("Transitions", 1080, 1920, 30).copy(
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
        )
    }

    private fun threeClipProject(): Project {
        val asset = videoAsset()
        return Project.create("Transitions", 1080, 1920, 30).copy(
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
                        Clip("c", asset.id, 6_000_000L, 3_000_000L, 6_000_000L, 9_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }

    private fun videoAsset() = Asset(
        id = "video",
        type = AssetType.VIDEO,
        uri = "content://video",
        sourceDurationUs = 12_000_000L,
        sizeBytes = 1L,
        displayName = "video.mp4",
        mimeType = "video/mp4",
        fingerprint = "video",
    )
}
