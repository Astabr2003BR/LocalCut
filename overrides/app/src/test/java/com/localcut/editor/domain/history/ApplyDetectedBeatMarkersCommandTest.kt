package com.localcut.editor.domain.history

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplyDetectedBeatMarkersCommandTest {
    @Test
    fun detectedBeatsReplaceOnlyTargetAutoMarkersAndPreserveManualMarkers() {
        val project = projectWithTargetClip().copy(
            beatMarkers = listOf(
                BeatMarker("manual-beat-500000", 500_000L, 1.0),
                BeatMarker("auto-beat-target-1", 1_200_000L, 0.4),
                BeatMarker("auto-beat-other-1", 2_700_000L, 0.6),
            ),
        )
        val command = ApplyDetectedBeatMarkersCommand(
            clipId = "target",
            detectedRelativeMarkers = listOf(
                BeatMarker("beat-1", 250_000L, 0.7),
                BeatMarker("beat-2", 1_250_000L, 0.9),
            ),
        )

        val applied = command.apply(project)

        assertEquals(
            listOf(
                "manual-beat-500000" to 500_000L,
                "auto-beat-target-1" to 1_250_000L,
                "auto-beat-target-2" to 2_250_000L,
                "auto-beat-other-1" to 2_700_000L,
            ),
            applied.project.beatMarkers.map { it.id to it.timeUs },
        )

        val undone = applied.inverse.apply(applied.project)
        assertEquals(project.beatMarkers, undone.project.beatMarkers)
    }

    @Test
    fun detectedBeatApplicationRejectsNonNeutralTimeMap() {
        val project = projectWithTargetClip().copy(
            tracks = projectWithTargetClip().tracks.map { track ->
                track.copy(
                    clips = track.clips.map { clip ->
                        if (clip.id == "target") clip.copy(timeMap = TimeMap(baseSpeed = 2.0)) else clip
                    },
                )
            },
        )

        assertThrows(IllegalArgumentException::class.java) {
            ApplyDetectedBeatMarkersCommand(
                clipId = "target",
                detectedRelativeMarkers = listOf(BeatMarker("beat-1", 250_000L, 1.0)),
            ).apply(project)
        }
    }

    private fun projectWithTargetClip(): Project {
        val asset = Asset(
            id = "target-asset",
            type = AssetType.VIDEO,
            uri = "content://video/target",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 100L,
            displayName = "target.mp4",
            mimeType = "video/mp4",
            fingerprint = "target-fingerprint",
        )
        return Project(
            id = "beats",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "Beat analysis",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual",
                    type = TrackType.VISUAL,
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
