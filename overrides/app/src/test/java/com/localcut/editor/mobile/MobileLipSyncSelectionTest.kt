package com.localcut.editor.mobile

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileLipSyncSelectionTest {
    private fun project(extraVisual: Boolean = false, extraAudio: Boolean = false, locked: Boolean = false, muted: Boolean = false): Project {
        val base = Project.create("Remote lip-sync", 1080, 1920, 30)
        val assets = listOf(
            Asset(id = "face", type = AssetType.IMAGE, uri = "content://test/face",
                sourceDurationUs = null, sizeBytes = 100, displayName = "face.jpg", mimeType = "image/jpeg",
                fingerprint = "face-fp"),
            Asset(id = "voice", type = AssetType.AUDIO, uri = "content://test/voice",
                sourceDurationUs = 13_000_000L, sizeBytes = 100, displayName = "voice.mp3", mimeType = "audio/mpeg",
                fingerprint = "voice-fp"),
        )
        fun clip(id: String, asset: String) = Clip(id = id, assetId = asset, startUs = 0,
            durationUs = 5_000_000L, sourceInUs = 0, sourceOutUs = 5_000_000L)
        return base.copy(assets = assets, tracks = base.tracks.map { track ->
            when (track.type) {
                TrackType.VISUAL -> track.copy(locked = locked, clips =
                    listOf(clip("face-clip", "face")) + if (extraVisual) listOf(clip("second-face", "face")) else emptyList())
                TrackType.AUDIO -> track.copy(muted = muted, clips =
                    listOf(clip("voice-clip", "voice")) + if (extraAudio) listOf(clip("second-voice", "voice")) else emptyList())
            }
        })
    }

    @Test fun selectsUniqueImageAndAudioRegardlessOfSelectedVoice() {
        val p = project()
        val selected = MobileLipSyncSelection.resolve(p, "voice-clip")
        assertEquals("face-clip", selected.imageClipId)
        assertEquals("voice-clip", selected.audioClipId)
    }

    @Test fun rejectsMultipleImagesOrMultipleAudioClips() {
        assertTrue(runCatching { MobileLipSyncSelection.resolve(project(extraVisual = true), null) }.isFailure)
        assertTrue(runCatching { MobileLipSyncSelection.resolve(project(extraAudio = true), null) }.isFailure)
    }

    @Test fun rejectsLockedVisualAndMutedAudio() {
        assertTrue(runCatching { MobileLipSyncSelection.resolve(project(locked = true), null) }.isFailure)
        assertTrue(runCatching { MobileLipSyncSelection.resolve(project(muted = true), null) }.isFailure)
    }
}
