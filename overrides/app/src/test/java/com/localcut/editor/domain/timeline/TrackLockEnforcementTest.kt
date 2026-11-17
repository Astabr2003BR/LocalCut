package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.history.AddClipTransitionCommand
import com.localcut.editor.domain.history.DeleteAllVisualClipsCommand
import com.localcut.editor.domain.history.DeleteClipWithTransitionsCommand
import com.localcut.editor.domain.history.InsertClipAtTimelineCommand
import com.localcut.editor.domain.history.SetClipEffectsCommand
import com.localcut.editor.domain.history.SetClipOpacityCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import com.localcut.editor.domain.model.EffectInstance
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
    fun lockedAncestorRejectsDirectTimelineAndCreativeCommands() {
        val source = lockedProject().copy(
            tracks = lockedProject().tracks.map { track -> track.copy(locked = false) },
            compositionGraph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(id = "group-1", kind = CompositionNodeKind.GROUP, locked = true),
                    CompositionNode(
                        id = "layer-a",
                        kind = CompositionNodeKind.LAYER,
                        clipId = "a",
                        parentId = "group-1",
                    ),
                ),
            ),
        )

        assertTrackLocked { TimelineOps.moveClip(source, "a", 500_000L) }
        assertTrackLocked { TimelineOps.deleteClip(source, "a") }
        assertTrackLocked { DeleteClipWithTransitionsCommand("a").apply(source) }
        assertTrackLocked { TimelineOps.duplicateClip(source, "a", "a-copy") }
        assertTrackLocked { TimelineOps.removeImportedAssetClip(source, "visual-1", "video", "a") }
        assertTrackLocked {
            SetClipEffectsCommand("a", listOf(EffectInstance("fx-1", "blur"))).apply(source)
        }

        assertEquals(0L, source.findClip("a")!!.startUs)
        assertEquals(listOf("a", "b"), source.tracks.first { it.id == "visual-1" }.clips.map { it.id })
        assertTrue(source.findClip("a")!!.effects.isEmpty())
    }

    @Test
    fun insertModeRejectsShiftedClipWithLockedAncestorWithoutChangingSource() {
        val base = lockedProject()
        val source = base.copy(
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") track.copy(locked = false) else track
            },
            compositionGraph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(id = "group-b", kind = CompositionNodeKind.GROUP, locked = true),
                    CompositionNode(
                        id = "layer-b",
                        kind = CompositionNodeKind.LAYER,
                        clipId = "b",
                        parentId = "group-b",
                    ),
                ),
            ),
        )
        val before = source

        assertTrackLocked {
            InsertClipAtTimelineCommand(
                "visual-1",
                Clip("inserted", "video", 3_000_000L, 1_000_000L, 0L, 1_000_000L),
            ).apply(source)
        }

        assertEquals(before, source)
    }

    @Test
    fun insertModeDoesNotBlockLockedAncestorOutsideShiftedRange() {
        val base = lockedProject()
        val source = base.copy(
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") track.copy(locked = false) else track
            },
            compositionGraph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(id = "group-a", kind = CompositionNodeKind.GROUP, locked = true),
                    CompositionNode(
                        id = "layer-a",
                        kind = CompositionNodeKind.LAYER,
                        clipId = "a",
                        parentId = "group-a",
                    ),
                ),
            ),
        )

        val edited = InsertClipAtTimelineCommand(
            "visual-1",
            Clip("inserted", "video", 3_000_000L, 1_000_000L, 0L, 1_000_000L),
        ).apply(source).project

        assertEquals(0L, edited.findClip("a")!!.startUs)
        assertEquals(4_000_000L, edited.findClip("b")!!.startUs)
        assertEquals(3_000_000L, edited.findClip("inserted")!!.startUs)
    }

    @Test
    fun duplicateRejectsShiftedClipWithLockedAncestorWithoutChangingSource() {
        val base = lockedProject()
        val source = base.copy(
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") track.copy(locked = false) else track
            },
            compositionGraph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(id = "group-b", kind = CompositionNodeKind.GROUP, locked = true),
                    CompositionNode(
                        id = "layer-b",
                        kind = CompositionNodeKind.LAYER,
                        clipId = "b",
                        parentId = "group-b",
                    ),
                ),
            ),
        )
        val before = source

        assertTrackLocked {
            TimelineOps.duplicateClip(source, "a", "a-copy")
        }

        assertEquals(before, source)
    }

    @Test
    fun duplicateDoesNotBlockLockedAncestorOutsideShiftedRange() {
        val base = lockedProject()
        val earlier = Clip("earlier", "video", 0L, 1_000_000L, 0L, 1_000_000L)
        val sourceClip = Clip("a", "video", 1_000_000L, 2_000_000L, 1_000_000L, 3_000_000L)
        val following = Clip("b", "video", 3_000_000L, 3_000_000L, 3_000_000L, 6_000_000L)
        val source = base.copy(
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") {
                    track.copy(locked = false, clips = listOf(earlier, sourceClip, following))
                } else track
            },
            compositionGraph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(id = "group-earlier", kind = CompositionNodeKind.GROUP, locked = true),
                    CompositionNode(
                        id = "layer-earlier",
                        kind = CompositionNodeKind.LAYER,
                        clipId = "earlier",
                        parentId = "group-earlier",
                    ),
                ),
            ),
        )

        val edited = TimelineOps.duplicateClip(source, "a", "a-copy")

        assertEquals(0L, edited.findClip("earlier")!!.startUs)
        assertEquals(1_000_000L, edited.findClip("a")!!.startUs)
        assertEquals(3_000_000L, edited.findClip("a-copy")!!.startUs)
        assertEquals(5_000_000L, edited.findClip("b")!!.startUs)
        assertEquals(0L, source.findClip("b")!!.startUs + 3_000_000L)
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
