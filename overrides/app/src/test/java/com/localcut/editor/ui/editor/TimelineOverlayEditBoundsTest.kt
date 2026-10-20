package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineOverlayEditBoundsTest {
    private fun overlayTrack(): Track = Track(
        id = "overlay-1",
        type = TrackType.VISUAL,
        order = 1,
        collisionPolicy = TrackCollisionPolicy.OVERLAY,
        clips = listOf(
            Clip("previous", "asset", 0, 2_000_000, 0, 2_000_000),
            Clip("middle", "asset", 2_000_000, 2_000_000, 2_000_000, 4_000_000),
            Clip("next", "asset", 4_000_000, 2_000_000, 4_000_000, 6_000_000),
        ),
    )

    @Test
    fun overlayMoveCanCrossNeighbourClipsButNotTimelineStart() {
        val track = overlayTrack()

        assertEquals(500_000L, TimelineEditBounds.clampMove(track, "middle", 500_000L))
        assertEquals(5_000_000L, TimelineEditBounds.clampMove(track, "middle", 5_000_000L))
        assertEquals(0L, TimelineEditBounds.clampMove(track, "middle", -1_000_000L))
    }

    @Test
    fun overlayLeftTrimIgnoresPreviousClipButKeepsPositiveDuration() {
        val track = overlayTrack()

        assertEquals(500_000L, TimelineEditBounds.clampLeft(track, "middle", 500_000L))
        assertEquals(0L, TimelineEditBounds.clampLeft(track, "middle", -1L))
        assertEquals(3_999_999L, TimelineEditBounds.clampLeft(track, "middle", 5_000_000L))
    }

    @Test
    fun overlayRightTrimIgnoresNextClipButKeepsPositiveDuration() {
        val track = overlayTrack()

        assertEquals(5_000_000L, TimelineEditBounds.clampRight(track, "middle", 5_000_000L))
        assertEquals(2_000_001L, TimelineEditBounds.clampRight(track, "middle", 1_000_000L))
    }

    @Test
    fun overlayPlannerBoundsDoNotUseNeighbours() {
        val track = overlayTrack()

        assertEquals(0L..Long.MAX_VALUE, TimelineEditBounds.bounds(track, "middle", TimelineHitRegion.BODY))
        assertEquals(0L..3_999_999L, TimelineEditBounds.bounds(track, "middle", TimelineHitRegion.LEFT_HANDLE))
        assertEquals(2_000_001L..Long.MAX_VALUE, TimelineEditBounds.bounds(track, "middle", TimelineHitRegion.RIGHT_HANDLE))
    }
}
