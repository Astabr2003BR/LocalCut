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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RippleDeleteClipCommandTest {
    @Test
    fun rippleDeleteRemovesClipClosesItsDurationAndPreservesFollowingTransition() {
        val initial = sequentialProject()
        val history = HistoryManager(initial)

        history.execute(RippleDeleteClipCommand("b"))

        val edited = history.project
        assertNull(edited.findClip("b"))
        assertEquals(1_000_000L, edited.findClip("c")!!.startUs)
        assertEquals(2_000_000L, edited.findClip("d")!!.startUs)
        assertEquals(listOf("cd"), edited.transitions.map { it.id })

        assertTrue(history.undo())
        assertEquals(initial, history.project)

        assertTrue(history.redo())
        assertNull(history.project.findClip("b"))
        assertEquals(1_000_000L, history.project.findClip("c")!!.startUs)
    }

    @Test
    fun rippleDeleteKeepsPreExistingGapAfterRemovedClip() {
        val initial = sequentialProject().let { project ->
            val visual = project.tracks.first()
            val shifted = visual.copy(
                clips = visual.clips.map { clip ->
                    if (clip.id in setOf("c", "d")) clip.copy(startUs = clip.startUs + 500_000L) else clip
                },
            )
            project.copy(
                tracks = listOf(shifted, project.tracks[1]),
                transitions = emptyList(),
            )
        }

        val result = RippleDeleteClipCommand("b").apply(initial).project

        assertEquals(1_500_000L, result.findClip("c")!!.startUs)
        assertEquals(2_500_000L, result.findClip("d")!!.startUs)
    }

    @Test
    fun rippleDeleteRejectsOverlayTrack() {
        val initial = sequentialProject()
        val overlay = initial.tracks.first().copy(collisionPolicy = TrackCollisionPolicy.OVERLAY)
        val project = initial.copy(tracks = listOf(overlay, initial.tracks[1]), transitions = emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            RippleDeleteClipCommand("b").apply(project)
        }
    }

    private fun sequentialProject(): Project {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        val clips = listOf(
            Clip("a", asset.id, 0L, 1_000_000L, 0L, 1_000_000L),
            Clip("b", asset.id, 1_000_000L, 1_000_000L, 1_000_000L, 2_000_000L),
            Clip("c", asset.id, 2_000_000L, 1_000_000L, 2_000_000L, 3_000_000L),
            Clip("d", asset.id, 3_000_000L, 1_000_000L, 3_000_000L, 4_000_000L),
        )
        return Project.create("Ripple delete", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = clips,
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            transitions = listOf(
                ClipTransition("ab", "a", "b", 200_000L, TransitionKind.FADE_TO_BLACK),
                ClipTransition("bc", "b", "c", 200_000L, TransitionKind.FADE_TO_BLACK),
                ClipTransition("cd", "c", "d", 200_000L, TransitionKind.FADE_TO_BLACK),
            ),
        )
    }
}
