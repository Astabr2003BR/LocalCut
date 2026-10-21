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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VisualOverlayImportCommandTest {
    @Test
    fun firstOverlayImportCreatesOverlayTrackAndUndoRestoresExactProject() {
        val initial = Project.create("Overlay", 1080, 1920, 30)
        val asset = imageAsset("asset-1")
        val clip = imageClip("clip-1", asset.id, 1_000_000L, 2_000_000L)
        val history = HistoryManager(initial)

        history.execute(InsertVisualOverlayAssetClipCommand(asset, clip))

        val after = history.project
        val overlayTrack = after.tracks.single { it.type == TrackType.VISUAL && it.collisionPolicy == TrackCollisionPolicy.OVERLAY }
        assertEquals("visual-overlay-1", overlayTrack.id)
        assertEquals(listOf(clip), overlayTrack.clips)
        assertNotNull(after.assets.firstOrNull { it.id == asset.id })

        assertTrue(history.undo())
        assertEquals(initial, history.project)

        assertTrue(history.redo())
        assertEquals(after, history.project)
    }

    @Test
    fun laterOverlayImportReusesExistingOverlayTrackAndUndoKeepsTrack() {
        val overlayTrack = Track(
            id = "overlay-existing",
            type = TrackType.VISUAL,
            order = 7,
            collisionPolicy = TrackCollisionPolicy.OVERLAY,
        )
        val initial = Project.create("Overlay", 1080, 1920, 30).copy(
            tracks = Project.create("Overlay", 1080, 1920, 30).tracks + overlayTrack,
        )
        val asset = imageAsset("asset-2")
        val clip = imageClip("clip-2", asset.id, 500_000L, 2_000_000L)
        val history = HistoryManager(initial)

        history.execute(InsertVisualOverlayAssetClipCommand(asset, clip))

        assertEquals(initial.tracks.size, history.project.tracks.size)
        assertEquals(listOf(clip), history.project.tracks.single { it.id == overlayTrack.id }.clips)
        assertTrue(history.undo())
        assertEquals(initial, history.project)
    }

    @Test
    fun audioAssetCannotBeImportedAsVisualOverlay() {
        val initial = Project.create("Overlay", 1080, 1920, 30)
        val asset = Asset(
            id = "audio",
            type = AssetType.AUDIO,
            uri = "content://audio",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 1L,
            displayName = "audio.m4a",
            mimeType = "audio/mp4",
            fingerprint = "audio",
        )
        val clip = imageClip("audio-clip", asset.id, 0L, 2_000_000L)

        try {
            InsertVisualOverlayAssetClipCommand(asset, clip).apply(initial)
            fail("audio overlay import should fail")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun imageAsset(id: String): Asset = Asset(
        id = id,
        type = AssetType.IMAGE,
        uri = "content://$id",
        sourceDurationUs = null,
        sizeBytes = 1L,
        displayName = "$id.png",
        mimeType = "image/png",
        fingerprint = id,
    )

    private fun imageClip(id: String, assetId: String, startUs: Long, durationUs: Long): Clip = Clip(
        id = id,
        assetId = assetId,
        startUs = startUs,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
    )
}
