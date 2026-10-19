package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineTrackGeometryTest {
    private val tracks = listOf(
        Track("audio-2", TrackType.AUDIO, order = 3),
        Track("visual-main", TrackType.VISUAL, order = 0),
        Track("visual-overlay", TrackType.VISUAL, order = 1),
        Track("audio-1", TrackType.AUDIO, order = 2),
    )

    @Test
    fun layoutSortsTracksByOrderAndProducesStableRows() {
        val layout = TimelineTrackGeometry.layout(
            tracks = tracks,
            rowHeightDp = 58f,
            rowGapDp = 6f,
        )

        assertEquals(
            listOf("visual-main", "visual-overlay", "audio-1", "audio-2"),
            layout.rows.map { it.trackId },
        )
        assertEquals(0f, layout.rows[0].topDp, 0f)
        assertEquals(58f, layout.rows[0].bottomDp, 0f)
        assertEquals(64f, layout.rows[1].topDp, 0f)
        assertEquals(250f, layout.contentHeightDp, 0f)
    }

    @Test
    fun hitTestUsesTopInclusiveBottomExclusiveAndIgnoresGaps() {
        val layout = TimelineTrackGeometry.layout(tracks, 58f, 6f)

        assertEquals("visual-main", layout.trackIdAt(0f))
        assertEquals("visual-main", layout.trackIdAt(57.999f))
        assertNull(layout.trackIdAt(58f))
        assertNull(layout.trackIdAt(63.999f))
        assertEquals("visual-overlay", layout.trackIdAt(64f))
        assertEquals("audio-2", layout.trackIdAt(249.999f))
        assertNull(layout.trackIdAt(250f))
        assertNull(layout.trackIdAt(-0.001f))
    }

    @Test
    fun emptyLayoutHasZeroHeightAndNoHit() {
        val layout = TimelineTrackGeometry.layout(emptyList(), 58f, 6f)

        assertEquals(emptyList<TimelineTrackRow>(), layout.rows)
        assertEquals(0f, layout.contentHeightDp, 0f)
        assertNull(layout.trackIdAt(0f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveRowHeightIsRejected() {
        TimelineTrackGeometry.layout(tracks, 0f, 6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeGapIsRejected() {
        TimelineTrackGeometry.layout(tracks, 58f, -1f)
    }
}
