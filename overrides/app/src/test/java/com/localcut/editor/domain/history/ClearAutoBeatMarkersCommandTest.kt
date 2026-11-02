package com.localcut.editor.domain.history

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Test

class ClearAutoBeatMarkersCommandTest {
    @Test
    fun cleanupRemovesOnlyTargetAutomaticMarkersAndUndoRestoresAll() {
        val project = projectWithClip().copy(
            beatMarkers = listOf(
                BeatMarker("manual-beat-500000", 500_000L, 1.0),
                BeatMarker("auto-beat-target-1", 1_200_000L, 0.8),
                BeatMarker("auto-beat-target-2", 1_800_000L, 0.7),
                BeatMarker("auto-beat-other-1", 2_500_000L, 0.6),
            ),
        )

        val cleared = ClearAutoBeatMarkersCommand("target").apply(project)

        assertEquals(
            listOf("manual-beat-500000", "auto-beat-other-1"),
            cleared.project.beatMarkers.map { it.id },
        )

        val undone = cleared.inverse.apply(cleared.project)
        assertEquals(project.beatMarkers, undone.project.beatMarkers)
    }

    private fun projectWithClip(): Project {
        val asset = Asset(
            id = "asset",
            type = AssetType.AUDIO,
            uri = "content://audio/test",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 100L,
            displayName = "test.wav",
            mimeType = "audio/wav",
            fingerprint = "audio-fingerprint",
        )
        return Project(
            id = "beats-cleanup",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Beat cleanup",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "audio",
                    type = TrackType.AUDIO,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip(
                            id = "target",
                            assetId = asset.id,
                            startUs = 1_000_000L,
                            durationUs = 2_000_000L,
                            sourceInUs = 0L,
                            sourceOutUs = 2_000_000L,
                        ),
                    ),
                ),
            ),
        )
    }
}
