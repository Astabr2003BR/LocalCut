package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeatMarkerUiPolicyTest {
    @Test
    fun nearestMarkerUsesThirtyMillisecondMobileTolerance() {
        val markers = listOf(
            BeatMarker("a", 1_000_000L, 0.5),
            BeatMarker("b", 2_000_000L, 1.0),
        )

        assertEquals("b", BeatMarkerUiPolicy.nearestMarker(markers, 2_025_000L)?.id)
        assertNull(BeatMarkerUiPolicy.nearestMarker(markers, 2_031_000L))
    }

    @Test
    fun editSnapUsesEightyMillisecondToleranceAndOtherwisePreservesRequest() {
        val markers = listOf(
            BeatMarker("a", 1_000_000L, 1.0),
            BeatMarker("b", 2_000_000L, 0.7),
        )

        assertEquals(2_000_000L, BeatMarkerUiPolicy.snapTimeUs(markers, 1_925_000L))
        assertEquals(1_919_000L, BeatMarkerUiPolicy.snapTimeUs(markers, 1_919_000L))
    }

    @Test
    fun emptyProjectCannotCreateManualBeatMarker() {
        val project = Project.create("Beat", 1080, 1920, 30)
        assertNull(BeatMarkerUiPolicy.manualMarker(project, 0L))
    }

    @Test
    fun nearestMarkerTieBreaksByEarlierTimeForDeterminism() {
        val markers = listOf(
            BeatMarker("right", 1_010_000L, 1.0),
            BeatMarker("left", 990_000L, 1.0),
        )

        assertEquals("left", BeatMarkerUiPolicy.nearestMarker(markers, 1_000_000L)?.id)
    }
}
