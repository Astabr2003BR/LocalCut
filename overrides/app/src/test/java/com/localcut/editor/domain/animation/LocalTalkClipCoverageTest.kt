package com.localcut.editor.domain.animation

import com.localcut.editor.domain.history.ApplyConfirmedTalkCommand
import com.localcut.editor.domain.history.HistoryManager
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTalkClipCoverageTest {
    private fun project(extraVisual: Boolean = false, locked: Boolean = false): Project {
        val base = Project.create("Talk coverage", 1080, 1920, 30)
        val assets = listOf(
            Asset(id = "image", type = AssetType.IMAGE, uri = "content://test/image", sourceDurationUs = null,
                sizeBytes = 200, displayName = "image.png", mimeType = "image/png", fingerprint = "img-fp"),
            Asset(id = "voice", type = AssetType.AUDIO, uri = "content://test/voice", sourceDurationUs = 13_000_000L,
                sizeBytes = 200, displayName = "voice.mp3", mimeType = "audio/mpeg", fingerprint = "voice-fp"),
        )
        val image = Clip("body", "image", 0, 5_000_000L, 0, 5_000_000L)
        val audio = Clip("voice", "voice", 0, 12_540_000L, 0, 12_540_000L)
        return base.copy(
            assets = assets,
            tracks = base.tracks.map { track ->
                when (track.type) {
                    TrackType.VISUAL -> track.copy(locked = locked, clips = listOf(image) +
                        if (extraVisual) listOf(Clip("other", "image", 6_000_000L, 2_000_000L, 0, 2_000_000L)) else emptyList())
                    TrackType.AUDIO -> track.copy(clips = listOf(audio))
                }
            },
        )
    }

    @Test fun extendsImageToCoverAllTwelveSecondsWithoutChangingVoice() {
        val original = project()
        val extended = LocalTalkClipCoverage.projectedProject(original, "body", "voice")
        assertEquals(12_540_000L, extended.findClip("body")?.durationUs)
        assertEquals(12_540_000L, extended.findClip("body")?.sourceOutUs)
        assertEquals(original.findClip("voice"), extended.findClip("voice"))
        assertEquals(5_000_000L, original.findClip("body")?.durationUs)
    }

    @Test fun recoversImportedFiveSecondVoiceWithKnownLongerSource() {
        val base = project()
        val short = base.copy(tracks = base.tracks.map { track ->
            if (track.type != TrackType.AUDIO) track else track.copy(
                clips = track.clips.map { it.copy(durationUs = 5_000_000L, sourceOutUs = 5_000_000L) },
            )
        })
        val recovered = LocalTalkClipCoverage.projectedProject(short, "body", "voice")
        assertEquals(13_000_000L, recovered.findClip("voice")?.durationUs)
        assertEquals(13_000_000L, recovered.findClip("body")?.durationUs)
        assertEquals(5_000_000L, short.findClip("voice")?.durationUs)
        val slice = AudioDrivenTalkPlanner.prepare(recovered, "body", "voice")
        assertEquals(13_000_000L, slice.durationUs)
    }

    @Test fun verifiedLocalMetadataRepairsFiveSecondDefaultAndRemainsUndoable() {
        val base = project()
        val short = base.copy(
            assets = base.assets.map { asset ->
                if (asset.id == "voice") asset.copy(sourceDurationUs = null) else asset
            },
            tracks = base.tracks.map { track ->
                if (track.type != TrackType.AUDIO) track else track.copy(
                    clips = track.clips.map {
                        it.copy(durationUs = 5_000_000L, sourceOutUs = 5_000_000L)
                    },
                )
            },
        )
        val corrected = LocalTalkClipCoverage.projectedProject(
            short, "body", "voice", verifiedAudioDurationUs = 12_538_000L,
        )
        assertEquals(12_538_000L, corrected.findClip("body")?.durationUs)
        assertEquals(12_538_000L, corrected.findClip("voice")?.durationUs)
        assertEquals(12_538_000L, corrected.assets.single { it.id == "voice" }.sourceDurationUs)
        assertEquals(null, short.assets.single { it.id == "voice" }.sourceDurationUs)

        val slice = AudioDrivenTalkPlanner.prepare(corrected, "body", "voice")
        val plan = AudioDrivenTalkPlanner.plan(
            corrected, slice, MouthRegion(0.51, 0.49, 0.07, 0.025),
            listOf(LipSyncCue(350_000L, 400_000L, 0.7)),
        )
        val history = HistoryManager(short)
        history.execute(ApplyConfirmedTalkCommand(
            "body", "voice", plan, verifiedAudioDurationUs = 12_538_000L,
        ))
        assertEquals(12_538_000L, history.project.findClip("voice")?.durationUs)
        assertTrue(history.undo())
        assertEquals(short, history.project)
        assertTrue(history.redo())
        assertEquals(12_538_000L, history.project.findClip("body")?.durationUs)
    }

    @Test fun invalidOrAmbiguousVerifiedDurationIsRejected() {
        val base = project()
        assertTrue(runCatching {
            LocalTalkClipCoverage.projectedProject(base, "body", "voice", verifiedAudioDurationUs = -1L)
        }.isFailure)
        assertTrue(runCatching {
            LocalTalkClipCoverage.projectedProject(
                base, "body", "voice",
                verifiedAudioDurationUs = AudioDrivenTalkPlanner.MAX_DURATION_US + 1L,
            )
        }.isFailure)
    }

    @Test fun intentionallyTrimmedFourSecondVoiceIsNeverExtended() {
        val base = project()
        val trimmed = base.copy(tracks = base.tracks.map { track ->
            if (track.type != TrackType.AUDIO) track else track.copy(
                clips = track.clips.map { it.copy(durationUs = 4_000_000L, sourceOutUs = 4_000_000L) },
            )
        })
        val recovered = LocalTalkClipCoverage.projectedProject(trimmed, "body", "voice")
        assertEquals(4_000_000L, recovered.findClip("voice")?.durationUs)
        assertEquals(5_000_000L, recovered.findClip("body")?.durationUs)
    }

    @Test fun voiceRecoveryRejectsLockedAudioTrackOrOverlappingVisualClip() {
        val base = project()
        val short = base.copy(tracks = base.tracks.map { track ->
            if (track.type != TrackType.AUDIO) track else track.copy(
                clips = track.clips.map { it.copy(durationUs = 5_000_000L, sourceOutUs = 5_000_000L) },
                locked = true,
            )
        })
        assertTrue(runCatching {
            LocalTalkClipCoverage.projectedProject(short, "body", "voice")
        }.isFailure)
        val collision = project(extraVisual = true).copy(tracks = project(extraVisual = true).tracks.map { track ->
            if (track.type != TrackType.AUDIO) track else track.copy(
                clips = track.clips.map { it.copy(durationUs = 5_000_000L, sourceOutUs = 5_000_000L) },
            )
        })
        assertTrue(runCatching {
            LocalTalkClipCoverage.projectedProject(collision, "body", "voice")
        }.isFailure)
    }

    @Test fun neverOverwritesAnotherVisualClipOrBypassesTrackLock() {
        assertTrue(runCatching { LocalTalkClipCoverage.projectedProject(
            project(extraVisual = true), "body", "voice") }.isFailure)
        assertTrue(runCatching { LocalTalkClipCoverage.projectedProject(
            project(locked = true), "body", "voice") }.isFailure)
    }

    @Test fun confirmedTalkAndExtensionAreOneUndoableAction() {
        val original = project()
        val projected = LocalTalkClipCoverage.projectedProject(original, "body", "voice")
        val slice = AudioDrivenTalkPlanner.prepare(projected, "body", "voice")
        val plan = AudioDrivenTalkPlanner.plan(
            projected, slice, MouthRegion(0.51, 0.49, 0.07, 0.025),
            listOf(LipSyncCue(offsetUs = 350_000L, durationUs = 400_000L, openness = 0.7)),
        )
        val history = HistoryManager(original)
        history.execute(ApplyConfirmedTalkCommand("body", "voice", plan))
        assertEquals(12_540_000L, history.project.findClip("body")?.durationUs)
        assertTrue(history.project.findClip("body")?.effects?.isNotEmpty() == true)
        assertTrue(history.undo())
        assertEquals(original, history.project)
        assertTrue(history.redo())
        assertEquals(12_540_000L, history.project.findClip("body")?.durationUs)
    }
}
