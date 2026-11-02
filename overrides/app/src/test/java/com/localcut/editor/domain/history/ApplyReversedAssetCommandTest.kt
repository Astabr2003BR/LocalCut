package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyReversedAssetCommandTest {
    @Test
    fun replacesOnlyMediaSourceAndUndoRestoresOriginalProject() {
        val originalAsset = videoAsset("original", 10_000_000L)
        val reversedAsset = videoAsset("reversed", 10_000_000L)
        val originalClip = Clip(
            id = "clip",
            assetId = originalAsset.id,
            startUs = 4_000_000L,
            durationUs = 5_000_000L,
            sourceInUs = 2_000_000L,
            sourceOutUs = 7_000_000L,
        )
        val project = Project.create("Reverse", 1080, 1920, 30).copy(
            assets = listOf(originalAsset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(originalClip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )

        val applied = ApplyReversedAssetCommand(originalClip.id, reversedAsset).apply(project)
        val replaced = requireNotNull(applied.project.findClip(originalClip.id))

        assertEquals(reversedAsset.id, replaced.assetId)
        assertEquals(3_000_000L, replaced.sourceInUs)
        assertEquals(8_000_000L, replaced.sourceOutUs)
        assertEquals(originalClip.startUs, replaced.startUs)
        assertEquals(originalClip.durationUs, replaced.durationUs)
        assertEquals(originalClip.transform, replaced.transform)
        assertEquals(originalClip.effects, replaced.effects)
        assertEquals(listOf("original", "reversed"), applied.project.assets.map { it.id })

        val undone = applied.inverse.apply(applied.project).project
        assertEquals(project, undone)
    }

    @Test
    fun rejectsMissingClipDuplicateAssetAndNonVideoReplacement() {
        val originalAsset = videoAsset("original", 10_000_000L)
        val originalClip = Clip("clip", originalAsset.id, 0L, 10_000_000L, 0L, 10_000_000L)
        val project = Project.create("Reverse", 1080, 1920, 30).copy(
            assets = listOf(originalAsset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(originalClip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )

        assertTrue(runCatching {
            ApplyReversedAssetCommand("missing", videoAsset("reversed", 10_000_000L)).apply(project)
        }.isFailure)
        assertTrue(runCatching {
            ApplyReversedAssetCommand("clip", originalAsset).apply(project)
        }.isFailure)
        assertTrue(runCatching {
            ApplyReversedAssetCommand(
                "clip",
                Asset(
                    id = "audio",
                    type = AssetType.AUDIO,
                    uri = "content://audio",
                    sourceDurationUs = 10_000_000L,
                    sizeBytes = 1L,
                    displayName = "audio.m4a",
                    mimeType = "audio/mp4",
                    fingerprint = "audio",
                ),
            ).apply(project)
        }.isFailure)
    }

    private fun videoAsset(id: String, durationUs: Long): Asset = Asset(
        id = id,
        type = AssetType.VIDEO,
        uri = "content://$id",
        sourceDurationUs = durationUs,
        sizeBytes = 1L,
        displayName = "$id.mp4",
        mimeType = "video/mp4",
        fingerprint = id,
    )
}
