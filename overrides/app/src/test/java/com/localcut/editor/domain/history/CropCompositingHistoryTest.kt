package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.CropSettings
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropCompositingHistoryTest {
    @Test
    fun cropCompositingIsUndoableAndRedoableWithoutLosingBoundsOrMode() {
        val initial = projectWithVideoClip()
        val history = HistoryManager(initial)
        val crop = CompositingSettings(
            crop = CropSettings(
                mode = CropMode.FILL,
                left = 0.10,
                top = 0.15,
                right = 0.85,
                bottom = 0.90,
            ),
        )

        history.execute(SetClipCompositingCommand("clip-1", crop))
        assertEquals(crop, history.project.findClip("clip-1")!!.compositing)

        assertTrue(history.undo())
        assertEquals(CompositingSettings(), history.project.findClip("clip-1")!!.compositing)

        assertTrue(history.redo())
        assertEquals(crop, history.project.findClip("clip-1")!!.compositing)
    }

    private fun projectWithVideoClip(): Project {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
        )
        return Project.create("Crop history", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
