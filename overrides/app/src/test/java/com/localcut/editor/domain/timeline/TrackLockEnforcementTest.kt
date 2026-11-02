package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.history.AddClipTransitionCommand
import com.localcut.editor.domain.history.DeleteAllVisualClipsCommand
import com.localcut.editor.domain.history.SetClipOpacityCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLockEnforcementTest {
    @Test
    fun lockedTrackRejectsCoreTimelineMutationsWithoutChangingSource() {
        val source = lockedProject()

        assertTrackLocked { TimelineOps.moveClip(source, "a", 500_000L) }
        assertTrackLocked { TimelineOps.setVolume(source, "a", 0.5) }
        assertTrackLocked { TimelineOps.trimClip(source, "a", 0L, 2_000_000L) }
        assertTrackLocked { TimelineOps.splitClip(source, "a", 1_000_000L) }
        assertTrackLocked { TimelineOps.deleteClip(source, "a") }
        assertTrackLocked {
            TimelineOps.insertClip(
                source,
                "visual-1",
                Clip("c", "video", 6_000_000L, 1_000_000L, 6_000_000L, 7_000_000L),
            )
        }

        assertEquals(listOf("a", "b"), source.tracks.first { it.id == "visual-1" }.clips.map { it.id })
        assertEquals(1.0, source.findClip("a")!!.volume, 0.0)
    }

    @Test
    fun lockedTrackRejectsCreativeClipMutationThatBypassesTimelineOps() {
        val source = lockedProject()

        assertTrackLocked {
            SetClipOpacityCommand("a", 0.5).apply(source)
        }

        assertEquals(1.0, source.findClip("a")!!.opacity, 0.0)
    }

    @Test
    fun lockedTrackRejectsTransitionMutation() {
        val source = lockedProject()
        val transition = ClipTransition(
            id = "t-ab",
            fromClipId = "a",
            toClipId = "b",
            durationUs = 500_000L,
            kind = TransitionKind.FADE_TO_BLACK,
        )

        assertTrackLocked {
            AddClipTransitionCommand(transition).apply(source)
        }

        assertTrue(source.transitions.isEmpty())
    }

    @Test
    fun bulkVisualDeleteCannotEraseLockedTrack() {
        val source = lockedProject()

        assertTrackLocked {
            DeleteAllVisualClipsCommand.apply(source)
        }

        assertEquals(listOf("a", "b"), source.tracks.first { it.id == "visual-1" }.clips.map { it.id })
    }

    @Test
    fun unlockedTrackRemainsEditable() {
        val source = lockedProject().let { project ->
            project.copy(
                tracks = project.tracks.map { track ->
                    if (track.id == "visual-1") track.copy(locked = false) else track
                },
            )
        }

        val moved = TimelineOps.moveClip(source, "b", 4_000_000L)

        assertEquals(4_000_000L, moved.findClip("b")!!.startUs)
    }

    private fun assertTrackLocked(block: () -> Unit) {
        val error = assertThrows(IllegalArgumentException::class.java) { block() }
        val message = error.message.orEmpty()
        assertTrue(
            "expected locked-track error, got: $message",
            message.contains("track locked", ignoreCase = true) || message.contains("TRACK_LOCKED"),
        )
    }

    private fun lockedProject(): Project {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 10_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        return Project.create("Locked track", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    locked = true,
                    clips = listOf(
                        Clip("a", asset.id, 0L, 3_000_000L, 0L, 3_000_000L),
                        Clip("b", asset.id, 3_000_000L, 3_000_000L, 3_000_000L, 6_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
