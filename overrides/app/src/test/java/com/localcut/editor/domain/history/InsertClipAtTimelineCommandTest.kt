package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertClipAtTimelineCommandTest {
    @Test
    fun insertShiftsFollowingClipsAndSupportsUndoRedo() {
        val initial = project()
        val history = HistoryManager(initial)
        val inserted = Clip("inserted", "video", 1_000_000L, 500_000L, 0L, 500_000L)

        history.execute(InsertClipAtTimelineCommand("visual", inserted))

        assertNotNull(history.project.findClip("inserted"))
        assertEquals(1_500_000L, history.project.findClip("b")!!.startUs)
        assertEquals(2_500_000L, history.project.findClip("c")!!.startUs)
        assertTrue(history.undo())
        assertEquals(initial, history.project)
        assertTrue(history.redo())
        assertEquals(1_500_000L, history.project.findClip("b")!!.startUs)
    }

    @Test
    fun insertPreservesPreExistingGapAfterInsertionPoint() {
        val initial = project(gapUs = 500_000L)
        val inserted = Clip("inserted", "video", 1_000_000L, 500_000L, 0L, 500_000L)

        val edited = InsertClipAtTimelineCommand("visual", inserted).apply(initial).project

        assertEquals(2_000_000L, edited.findClip("b")!!.startUs)
        assertEquals(3_000_000L, edited.findClip("c")!!.startUs)
    }

    @Test
    fun insertRejectsPointInsideExistingClip() {
        val inserted = Clip("inserted", "video", 500_000L, 250_000L, 0L, 250_000L)

        assertThrows(IllegalArgumentException::class.java) {
            InsertClipAtTimelineCommand("visual", inserted).apply(project())
        }
    }

    @Test
    fun insertRejectsLockedTrack() {
        val initial = project().let { project ->
            project.copy(tracks = project.tracks.map { track ->
                if (track.id == "visual") track.copy(locked = true) else track
            })
        }
        val inserted = Clip("inserted", "video", 1_000_000L, 500_000L, 0L, 500_000L)

        assertThrows(IllegalArgumentException::class.java) {
            InsertClipAtTimelineCommand("visual", inserted).apply(initial)
        }
    }

    @Test
    fun insertRejectsOverlayTrack() {
        val initial = project().let { project ->
            project.copy(tracks = project.tracks.map { track ->
                if (track.id == "visual") track.copy(collisionPolicy = TrackCollisionPolicy.OVERLAY) else track
            })
        }
        val inserted = Clip("inserted", "video", 1_000_000L, 500_000L, 0L, 500_000L)

        assertThrows(IllegalArgumentException::class.java) {
            InsertClipAtTimelineCommand("visual", inserted).apply(initial)
        }
    }

    private fun project(gapUs: Long = 0L): Project {
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
            Clip("b", asset.id, 1_000_000L + gapUs, 1_000_000L, 1_000_000L, 2_000_000L),
            Clip("c", asset.id, 2_000_000L + gapUs, 1_000_000L, 2_000_000L, 3_000_000L),
        )
        return Project.create("Insert mode", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = clips,
                ),
            ),
        )
    }
}
