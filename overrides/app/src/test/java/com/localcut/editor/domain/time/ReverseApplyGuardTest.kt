package com.localcut.editor.domain.time

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseApplyGuardTest {
    @Test
    fun remainsValidAcrossSafeClipEdits() {
        val project = project()
        val snapshot = ReverseApplyGuard.capture(project, "clip").getOrThrow()
        val moved = project.copy(
            tracks = project.tracks.map { track ->
                track.copy(clips = track.clips.map { clip ->
                    if (clip.id == "clip") clip.copy(startUs = 2_000_000L, opacity = 0.5) else clip
                })
            },
        )

        assertTrue(ReverseApplyGuard.isStillCurrent(moved, snapshot))
    }

    @Test
    fun rejectsChangedSourceTrimOrFingerprint() {
        val project = project()
        val snapshot = ReverseApplyGuard.capture(project, "clip").getOrThrow()
        val trimmed = project.withClip(project.findClip("clip")!!.copy(sourceInUs = 1_000_000L))
        val changedAsset = project.copy(
            assets = project.assets.map { asset ->
                if (asset.id == "video") asset.copy(fingerprint = "changed") else asset
            },
        )

        assertFalse(ReverseApplyGuard.isStillCurrent(trimmed, snapshot))
        assertFalse(ReverseApplyGuard.isStillCurrent(changedAsset, snapshot))
    }

    @Test
    fun rejectsFreezeReverseAndMissingClip() {
        val project = project()
        val snapshot = ReverseApplyGuard.capture(project, "clip").getOrThrow()
        val frozen = project.withClip(
            project.findClip("clip")!!.copy(
                timeMap = TimeMap(freezeSegments = listOf(FreezeSegment(100_000L, 100_000L, 100_000L))),
            ),
        )
        val reversed = project.withClip(project.findClip("clip")!!.copy(timeMap = TimeMap(reverse = true)))
        val missing = project.copy(tracks = project.tracks.map { it.copy(clips = emptyList()) })

        assertFalse(ReverseApplyGuard.isStillCurrent(frozen, snapshot))
        assertFalse(ReverseApplyGuard.isStillCurrent(reversed, snapshot))
        assertFalse(ReverseApplyGuard.isStillCurrent(missing, snapshot))
    }

    private fun project(): Project {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 10_000_000L,
            sizeBytes = 100L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fingerprint",
        )
        val clip = Clip("clip", asset.id, 0L, 5_000_000L, 2_000_000L, 7_000_000L)
        return Project.create("Guard", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }

    private fun Project.withClip(replacement: Clip): Project = copy(
        tracks = tracks.map { track ->
            track.copy(clips = track.clips.map { if (it.id == replacement.id) replacement else it })
        },
    )
}
