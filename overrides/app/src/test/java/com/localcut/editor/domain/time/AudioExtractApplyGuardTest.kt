package com.localcut.editor.domain.time

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExtractApplyGuardTest {
    @Test
    fun visualOnlyChangesRemainCurrentButAudioOrTimingChangesInvalidateResult() {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 10_000_000L,
            sizeBytes = 10L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fingerprint",
        )
        val clip = Clip("clip", asset.id, 1_000_000L, 5_000_000L, 2_000_000L, 7_000_000L)
        val project = Project.create("Guard", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(Track("visual", TrackType.VISUAL, 0, clips = listOf(clip))),
        )
        val snapshot = AudioExtractApplyGuard.capture(project, clip.id).getOrThrow()

        val visualOnly = project.copy(
            tracks = listOf(project.tracks.single().copy(
                clips = listOf(clip.copy(opacity = 0.5)),
            )),
        )
        assertTrue(AudioExtractApplyGuard.isStillCurrent(visualOnly, snapshot))

        val changedAudio = project.copy(
            tracks = listOf(project.tracks.single().copy(
                clips = listOf(clip.copy(volume = 0.5)),
            )),
        )
        assertFalse(AudioExtractApplyGuard.isStillCurrent(changedAudio, snapshot))

        val changedTiming = project.copy(
            tracks = listOf(project.tracks.single().copy(
                clips = listOf(clip.copy(startUs = 2_000_000L)),
            )),
        )
        assertFalse(AudioExtractApplyGuard.isStillCurrent(changedTiming, snapshot))
    }
}
