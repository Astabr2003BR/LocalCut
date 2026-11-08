package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.session.ProjectSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RippleDeleteUiPolicyTest {
    private fun project(): Project {
        val seed = Project.create("Ripple UX", 1080, 1920, 30)
        return seed.copy(tracks = seed.tracks.map { track ->
            if (track.type == TrackType.VISUAL) track.copy(
                clips = listOf(
                    Clip("a", "asset", 0L, 1_000_000L, 0L, 1_000_000L),
                    Clip("b", "asset", 1_000_000L, 1_000_000L, 1_000_000L, 2_000_000L),
                    Clip("c", "asset", 2_200_000L, 1_000_000L, 2_000_000L, 3_000_000L),
                    Clip("d", "asset", 3_200_000L, 1_000_000L, 3_000_000L, 4_000_000L),
                ),
            ) else track
        })
    }

    @Test
    fun exclusiveTrackShowsAffectedCountAndDuration() {
        val preview = requireNotNull(RippleDeleteUiPolicy.preview(project(), "b"))
        assertEquals(1_000_000L, preview.removedDurationUs)
        assertEquals(2, preview.followingClipCount)
        assertEquals("visual-1", preview.trackId)
    }

    @Test
    fun exclusiveTrackLastClipStillAllowsDeleteWithoutShift() {
        assertEquals(0, RippleDeleteUiPolicy.preview(project(), "d")!!.followingClipCount)
    }

    @Test
    fun overlayAndLockedTracksDoNotOfferRipple() {
        val original = project()
        val overlay = original.copy(tracks = original.tracks.map { track ->
            if (track.type == TrackType.VISUAL) track.copy(collisionPolicy = TrackCollisionPolicy.OVERLAY)
            else track
        })
        val locked = original.copy(tracks = original.tracks.map { track ->
            if (track.type == TrackType.VISUAL) track.copy(locked = true) else track
        })
        assertNull(RippleDeleteUiPolicy.preview(overlay, "b"))
        assertNull(RippleDeleteUiPolicy.preview(locked, "b"))
    }

    @Test
    fun staleRevisionOrSelectionCannotConfirmRipple() {
        val project = project()
        val preview = requireNotNull(RippleDeleteUiPolicy.preview(project, "b"))
        val valid = ProjectSessionState(project = project, selectedClipId = "b", revision = 42L)
        assertTrue(RippleDeleteUiPolicy.matchesCurrent(valid, 42L, preview))
        assertFalse(RippleDeleteUiPolicy.matchesCurrent(valid.copy(revision = 43L), 42L, preview))
        assertFalse(RippleDeleteUiPolicy.matchesCurrent(valid.copy(selectedClipId = "c"), 42L, preview))
        assertFalse(RippleDeleteUiPolicy.matchesCurrent(
            valid.copy(project = project.copy(title = "changed")), 42L,
            preview.copy(followingClipCount = 20),
        ))
    }

    @Test
    fun missingAndOverlappingClipsFailClosed() {
        assertNull(RippleDeleteUiPolicy.preview(project(), "missing"))
        val original = project()
        val overlap = original.copy(tracks = original.tracks.map { track ->
            if (track.type == TrackType.VISUAL) track.copy(clips = track.clips.map {
                if (it.id == "c") it.copy(startUs = 1_500_000L) else it
            }) else track
        })
        assertNull(RippleDeleteUiPolicy.preview(overlap, "b"))
    }
}
