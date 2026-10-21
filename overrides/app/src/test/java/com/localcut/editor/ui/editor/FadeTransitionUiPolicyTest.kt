package com.localcut.editor.ui.editor

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
import org.junit.Assert.assertNull
import org.junit.Test

class FadeTransitionUiPolicyTest {
    @Test
    fun adjacentExclusiveVisualBoundaryProducesDeterministicFade() {
        val project = sequentialProject()

        val fade = FadeTransitionUiPolicy.createOrUpdate(project, "a", 500_000L)!!

        assertEquals("transition-a-b", fade.id)
        assertEquals("a", fade.fromClipId)
        assertEquals("b", fade.toClipId)
        assertEquals(500_000L, fade.durationUs)
        assertEquals(TransitionKind.FADE_TO_BLACK, fade.kind)
    }

    @Test
    fun durationIsClampedToShortestAdjacentClip() {
        val project = sequentialProject(firstDurationUs = 300_000L, secondDurationUs = 700_000L)

        val fade = FadeTransitionUiPolicy.createOrUpdate(project, "a", 1_000_000L)!!

        assertEquals(300_000L, fade.durationUs)
    }

    @Test
    fun existingFadeKeepsIdentityWhenDurationChanges() {
        val existing = ClipTransition("custom-id", "a", "b", 250_000L, TransitionKind.FADE_TO_BLACK)
        val project = sequentialProject().copy(transitions = listOf(existing))

        val fade = FadeTransitionUiPolicy.createOrUpdate(project, "a", 750_000L)!!

        assertEquals("custom-id", fade.id)
        assertEquals(750_000L, fade.durationUs)
    }

    @Test
    fun unsupportedExistingTransitionIsNotSilentlyReplaced() {
        val existing = ClipTransition("dissolve", "a", "b", 250_000L, TransitionKind.DISSOLVE)
        val project = sequentialProject().copy(transitions = listOf(existing))

        assertNull(FadeTransitionUiPolicy.createOrUpdate(project, "a", 500_000L))
    }

    @Test
    fun gapLastClipAndOverlayTrackAreNotEligible() {
        val gapped = sequentialProject(secondStartUs = 3_100_000L)
        assertNull(FadeTransitionUiPolicy.createOrUpdate(gapped, "a", 500_000L))
        assertNull(FadeTransitionUiPolicy.createOrUpdate(sequentialProject(), "b", 500_000L))

        val overlay = sequentialProject().copy(
            tracks = sequentialProject().tracks.map { track ->
                if (track.id == "visual-1") track.copy(collisionPolicy = TrackCollisionPolicy.OVERLAY) else track
            },
        )
        assertNull(FadeTransitionUiPolicy.createOrUpdate(overlay, "a", 500_000L))
    }

    private fun sequentialProject(
        firstDurationUs: Long = 3_000_000L,
        secondDurationUs: Long = 3_000_000L,
        secondStartUs: Long = firstDurationUs,
    ): Project {
        val total = firstDurationUs + secondDurationUs
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = total,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        return Project.create("Transitions", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip("a", asset.id, 0L, firstDurationUs, 0L, firstDurationUs),
                        Clip(
                            "b",
                            asset.id,
                            secondStartUs,
                            secondDurationUs,
                            firstDurationUs,
                            firstDurationUs + secondDurationUs,
                        ),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
