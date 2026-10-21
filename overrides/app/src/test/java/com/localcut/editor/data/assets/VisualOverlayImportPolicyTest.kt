package com.localcut.editor.data.assets

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualOverlayImportPolicyTest {
    @Test
    fun imageOverlayStartsAtPlayheadAndClampsToPrimaryVisualEnd() {
        val project = projectWithPrimaryDuration(10_000_000L)
        val asset = imageAsset()

        val clip = VisualOverlayImportPolicy.create(
            project = project,
            asset = asset,
            clipId = "overlay",
            playheadUs = 9_500_000L,
        ).getOrThrow()

        assertEquals(9_500_000L, clip.startUs)
        assertEquals(500_000L, clip.durationUs)
        assertEquals(500_000L, clip.sourceOutUs)
    }

    @Test
    fun videoOverlayIsTrimmedToRemainingPrimaryVisualDuration() {
        val project = projectWithPrimaryDuration(10_000_000L)
        val asset = videoAsset(4_000_000L)

        val clip = VisualOverlayImportPolicy.create(project, asset, "overlay", 8_000_000L).getOrThrow()

        assertEquals(8_000_000L, clip.startUs)
        assertEquals(2_000_000L, clip.durationUs)
        assertEquals(2_000_000L, clip.sourceOutUs)
    }

    @Test
    fun playheadAtEndPlacesOverlayOnFinalFrame() {
        val project = projectWithPrimaryDuration(1_000_000L).copy(fps = 30)

        val clip = VisualOverlayImportPolicy.create(project, imageAsset(), "overlay", 1_000_000L).getOrThrow()

        assertEquals(966_667L, clip.startUs)
        assertEquals(33_333L, clip.durationUs)
    }

    @Test
    fun existingLongOverlayDoesNotExtendPrimaryVisualBoundary() {
        val base = projectWithPrimaryDuration(5_000_000L)
        val longOverlayTrack = Track(
            id = "overlay-track",
            type = TrackType.VISUAL,
            order = 3,
            collisionPolicy = TrackCollisionPolicy.OVERLAY,
            clips = listOf(
                Clip("old-overlay", "primary", 0L, 9_000_000L, 0L, 9_000_000L),
            ),
        )
        val project = base.copy(tracks = base.tracks + longOverlayTrack)

        val clip = VisualOverlayImportPolicy.create(project, imageAsset(), "new-overlay", 4_500_000L).getOrThrow()

        assertEquals(500_000L, clip.durationUs)
    }

    @Test
    fun overlayImportRequiresExistingPrimaryVisualTimelineAndNonAudioAsset() {
        val empty = Project.create("Empty", 1080, 1920, 30)
        assertTrue(VisualOverlayImportPolicy.create(empty, imageAsset(), "overlay", 0L).isFailure)
        assertTrue(VisualOverlayImportPolicy.create(projectWithPrimaryDuration(2_000_000L), audioAsset(), "overlay", 0L).isFailure)
    }

    private fun projectWithPrimaryDuration(durationUs: Long): Project {
        val asset = Asset(
            id = "primary",
            type = AssetType.VIDEO,
            uri = "content://primary",
            sourceDurationUs = durationUs,
            sizeBytes = 1L,
            displayName = "primary.mp4",
            mimeType = "video/mp4",
            fingerprint = "primary",
        )
        val clip = Clip("primary-clip", asset.id, 0L, durationUs, 0L, durationUs)
        return Project.create("Overlay", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, TrackCollisionPolicy.EXCLUSIVE, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }

    private fun imageAsset(): Asset = Asset(
        id = "image",
        type = AssetType.IMAGE,
        uri = "content://image",
        sourceDurationUs = null,
        sizeBytes = 1L,
        displayName = "image.png",
        mimeType = "image/png",
        fingerprint = "image",
    )

    private fun videoAsset(durationUs: Long): Asset = Asset(
        id = "video-overlay",
        type = AssetType.VIDEO,
        uri = "content://video-overlay",
        sourceDurationUs = durationUs,
        sizeBytes = 1L,
        displayName = "overlay.mp4",
        mimeType = "video/mp4",
        fingerprint = "video-overlay",
    )

    private fun audioAsset(): Asset = Asset(
        id = "audio",
        type = AssetType.AUDIO,
        uri = "content://audio",
        sourceDurationUs = 2_000_000L,
        sizeBytes = 1L,
        displayName = "audio.m4a",
        mimeType = "audio/mp4",
        fingerprint = "audio",
    )
}
