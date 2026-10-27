package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAllVisualClipsCommandTest {
    @Test
    fun removesAllVisualClipsPreservesAudioAssetsAndUndoRestoresExactProject() {
        val original = fixtureProject()
        val history = HistoryManager(original)

        history.execute(DeleteAllVisualClipsCommand)

        assertTrue(history.project.tracks.filter { it.type == TrackType.VISUAL }.all { it.clips.isEmpty() })
        assertNotNull(history.project.findClip("audio-clip"))
        assertEquals(original.assets, history.project.assets)
        assertTrue(TimelineValidator.validate(history.project).isEmpty())

        assertTrue(history.undo())
        assertEquals(original, history.project)
    }

    private fun fixtureProject(): Project {
        val video = Asset("video", AssetType.VIDEO, "content://video", 8_000_000L, 1L, "v.mp4", "video/mp4", "v")
        val image = Asset("image", AssetType.IMAGE, "content://image", null, 1L, "i.jpg", "image/jpeg", "i")
        val audio = Asset("audio", AssetType.AUDIO, "content://audio", 8_000_000L, 1L, "a.m4a", "audio/mp4", "a")
        return Project.create("bulk-delete", 1080, 1920, 30).copy(
            assets = listOf(video, image, audio),
            tracks = listOf(
                Track("visual-main", TrackType.VISUAL, 0, clips = listOf(
                    Clip("video-clip", "video", 0L, 2_000_000L, 0L, 2_000_000L),
                    Clip("image-clip", "image", 2_000_000L, 2_000_000L, 0L, 2_000_000L),
                )),
                Track("visual-overlay", TrackType.VISUAL, 1, clips = listOf(
                    Clip("overlay-clip", "image", 0L, 1_000_000L, 0L, 1_000_000L),
                )),
                Track("audio-main", TrackType.AUDIO, 2, clips = listOf(
                    Clip("audio-clip", "audio", 0L, 4_000_000L, 0L, 4_000_000L),
                )),
            ),
        )
    }
}
