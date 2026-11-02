package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineSnapUiPolicyTest {
    @Test
    fun toleranceTracksTimelineZoomInScreenSpace() {
        assertEquals(100_000L, TimelineSnapUiPolicy.toleranceUs(dpPerSecond = 80f, toleranceDp = 8f))
        assertEquals(50_000L, TimelineSnapUiPolicy.toleranceUs(dpPerSecond = 160f, toleranceDp = 8f))
    }

    @Test
    fun edgeSnapsToNearestCandidateInsideTolerance() {
        val candidates = listOf(1_000_000L, 2_000_000L, 3_000_000L)

        assertEquals(2_000_000L, TimelineSnapUiPolicy.snapEdge(1_940_000L, candidates, 80_000L))
        assertEquals(1_850_000L, TimelineSnapUiPolicy.snapEdge(1_850_000L, candidates, 80_000L))
    }

    @Test
    fun moveCanSnapEitherClipStartOrClipEnd() {
        val candidates = listOf(2_000_000L, 5_000_000L)

        val startSnap = TimelineSnapUiPolicy.snapMove(
            requestedStartUs = 1_950_000L,
            clipDurationUs = 1_000_000L,
            candidatesUs = candidates,
            toleranceUs = 80_000L,
        )
        assertEquals(2_000_000L, startSnap)

        val endSnap = TimelineSnapUiPolicy.snapMove(
            requestedStartUs = 4_040_000L,
            clipDurationUs = 1_000_000L,
            candidatesUs = candidates,
            toleranceUs = 80_000L,
        )
        assertEquals(4_000_000L, endSnap)
    }

    @Test
    fun candidateBuilderIncludesClipEdgesPlayheadAndOptionalBeatsWithoutSelectedClip() {
        val candidates = TimelineSnapUiPolicy.candidates(
            clipEdgesUs = listOf(
                TimelineSnapUiPolicy.ClipEdges("a", 0L, 1_000_000L),
                TimelineSnapUiPolicy.ClipEdges("selected", 1_000_000L, 2_000_000L),
                TimelineSnapUiPolicy.ClipEdges("b", 3_000_000L, 4_000_000L),
            ),
            excludedClipId = "selected",
            playheadUs = 2_500_000L,
            beatTimesUs = listOf(750_000L, 3_500_000L),
        )

        assertEquals(
            listOf(0L, 750_000L, 1_000_000L, 2_500_000L, 3_000_000L, 3_500_000L, 4_000_000L),
            candidates,
        )
        assertTrue(2_000_000L !in candidates)
    }
}
