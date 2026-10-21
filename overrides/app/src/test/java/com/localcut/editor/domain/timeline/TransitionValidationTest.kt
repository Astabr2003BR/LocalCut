package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionValidationTest {
    @Test
    fun adjacentVisualClipsOnExclusiveTrackAcceptBoundaryTransition() {
        val project = sequentialProject().copy(
            transitions = listOf(transition("a", "b", 500_000L)),
        )

        assertFalse(hasInvalidTransition(project))
    }

    @Test
    fun transitionRejectsGapOrNonAdjacentPair() {
        val gapped = sequentialProject(secondStartUs = 3_500_000L).copy(
            transitions = listOf(transition("a", "b", 500_000L)),
        )
        val nonAdjacent = sequentialProject(includeThirdClip = true).copy(
            transitions = listOf(transition("a", "c", 500_000L)),
        )

        assertTrue(hasInvalidTransition(gapped))
        assertTrue(hasInvalidTransition(nonAdjacent))
    }

    @Test
    fun transitionRejectsNonPositiveOrTooLongDuration() {
        val base = sequentialProject()

        assertTrue(hasInvalidTransition(base.copy(transitions = listOf(transition("a", "b", 0L)))))
        assertTrue(hasInvalidTransition(base.copy(transitions = listOf(transition("a", "b", 3_500_000L)))))
    }

    @Test
    fun transitionRejectsDifferentTrackOrOverlayTrack() {
        val base = sequentialProject()
        val differentTrack = base.copy(
            tracks = listOf(
                base.tracks.first().copy(clips = listOf(base.findClip("a")!!)),
                Track(
                    id = "visual-2",
                    type = TrackType.VISUAL,
                    order = 1,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(base.findClip("b")!!),
                ),
                base.tracks.last(),
            ),
            transitions = listOf(transition("a", "b", 500_000L)),
        )
        val overlayTrack = base.copy(
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") track.copy(collisionPolicy = TrackCollisionPolicy.OVERLAY) else track
            },
            transitions = listOf(transition("a", "b", 500_000L)),
        )

        assertTrue(hasInvalidTransition(differentTrack))
        assertTrue(hasInvalidTransition(overlayTrack))
    }

    private fun hasInvalidTransition(project: Project): Boolean =
        TimelineValidator.validate(project).any { it.code == ProjectIssueCode.INVALID_TRANSITION }

    private fun transition(from: String, to: String, durationUs: Long): ClipTransition = ClipTransition(
        id = "t-$from-$to-$durationUs",
        fromClipId = from,
        toClipId = to,
        durationUs = durationUs,
        kind = TransitionKind.DISSOLVE,
    )

    private fun sequentialProject(
        secondStartUs: Long = 3_000_000L,
        includeThirdClip: Boolean = false,
    ): Project {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 12_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        val clips = buildList {
            add(Clip("a", "video", 0L, 3_000_000L, 0L, 3_000_000L))
            add(Clip("b", "video", secondStartUs, 3_000_000L, 3_000_000L, 6_000_000L))
            if (includeThirdClip) {
                add(Clip("c", "video", secondStartUs + 3_000_000L, 3_000_000L, 6_000_000L, 9_000_000L))
            }
        }
        return Project.create("Transitions", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = clips,
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
