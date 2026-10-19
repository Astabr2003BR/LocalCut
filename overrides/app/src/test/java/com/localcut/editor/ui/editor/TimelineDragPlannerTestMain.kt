package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineOps
import com.localcut.editor.session.EditorAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TimelineDragPlannerTest {
    private fun project(): Project {
        val asset = Asset("a", AssetType.VIDEO, "content://a", 10_000_000, 1, "a.mp4", "video/mp4", "fp")
        val clip = Clip("c", "a", 1_000_000, 2_000_000, 0, 2_000_000)
        return Project.create("p", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }

    @Test
    fun bodyDragPreservesGrabOffset() {
        val p = project()
        val geometry = TimelineGeometry(100.0)
        val drag = TimelineDragPlanner.begin(p, "visual-1", 200.0, geometry, 12.0)!!

        assertEquals(TimelineHitRegion.BODY, drag.region)
        assertEquals(EditorAction.SelectClip("c"), drag.selectAction)
        assertEquals(EditorAction.MoveSelected(2_000_000), TimelineDragPlanner.actionAt(drag, 300.0, geometry))
    }

    @Test
    fun bodyDragClampsStartAtZero() {
        val p = project()
        val geometry = TimelineGeometry(100.0)
        val drag = TimelineDragPlanner.begin(p, "visual-1", 200.0, geometry, 12.0)!!

        assertEquals(EditorAction.MoveSelected(0), TimelineDragPlanner.actionAt(drag, 0.0, geometry))
    }

    @Test
    fun leftHandleDragCreatesLeftEdgeTrimAction() {
        val p = project()
        val geometry = TimelineGeometry(100.0)
        val drag = TimelineDragPlanner.begin(p, "visual-1", 105.0, geometry, 12.0)!!

        assertEquals(TimelineHitRegion.LEFT_HANDLE, drag.region)
        assertEquals(EditorAction.TrimSelectedLeftEdge(1_500_000), TimelineDragPlanner.actionAt(drag, 150.0, geometry))
    }

    @Test
    fun rightHandleDragCreatesRightEdgeTrimAction() {
        val p = project()
        val geometry = TimelineGeometry(100.0)
        val drag = TimelineDragPlanner.begin(p, "visual-1", 295.0, geometry, 12.0)!!

        assertEquals(TimelineHitRegion.RIGHT_HANDLE, drag.region)
        assertEquals(EditorAction.TrimSelectedRightEdge(3_500_000), TimelineDragPlanner.actionAt(drag, 350.0, geometry))
    }

    @Test
    fun touchOutsideAnyClipDoesNotBeginDrag() {
        assertNull(TimelineDragPlanner.begin(project(), "visual-1", 50.0, TimelineGeometry(100.0), 12.0))
    }

    @Test
    fun leftTrimCannotCrossPreviousSplitClip() {
        val source = Project.create("split", 1080, 1920, 30).copy(
            assets = listOf(Asset("v", AssetType.VIDEO, "content://v", 5_000_000, 1, "v.mp4", "video/mp4", "fp")),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(Clip("c", "v", 0, 5_000_000, 0, 5_000_000))),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
        val split = TimelineOps.splitClip(source, "c", 2_000_000)
        val geometry = TimelineGeometry(100.0)
        val drag = TimelineDragPlanner.begin(split, "visual-1", 205.0, geometry, 12.0)!!

        assertEquals(TimelineHitRegion.LEFT_HANDLE, drag.region)
        assertEquals(EditorAction.TrimSelectedLeftEdge(2_000_000), TimelineDragPlanner.actionAt(drag, 100.0, geometry))
    }

    @Test
    fun rightTrimCannotCrossNextSplitClip() {
        val source = Project.create("split", 1080, 1920, 30).copy(
            assets = listOf(Asset("v", AssetType.VIDEO, "content://v", 5_000_000, 1, "v.mp4", "video/mp4", "fp")),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(Clip("c", "v", 0, 5_000_000, 0, 5_000_000))),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
        val split = TimelineOps.splitClip(source, "c", 2_000_000)
        val geometry = TimelineGeometry(100.0)
        val drag = TimelineDragPlanner.begin(split, "visual-1", 195.0, geometry, 12.0)!!

        assertEquals(TimelineHitRegion.RIGHT_HANDLE, drag.region)
        assertEquals(EditorAction.TrimSelectedRightEdge(2_000_000), TimelineDragPlanner.actionAt(drag, 400.0, geometry))
    }

    @Test
    fun moveCannotCrossAdjacentClips() {
        val track = Track(
            id = "visual-1",
            type = TrackType.VISUAL,
            order = 0,
            clips = listOf(
                Clip("previous", "a", 0, 2_000_000, 0, 2_000_000),
                Clip("middle", "a", 2_000_000, 2_000_000, 2_000_000, 4_000_000),
                Clip("next", "a", 4_000_000, 2_000_000, 4_000_000, 6_000_000),
            ),
        )

        assertEquals(2_000_000L, TimelineEditBounds.clampMove(track, "middle", 500_000))
        assertEquals(2_000_000L, TimelineEditBounds.clampMove(track, "middle", 5_000_000))
    }

    @Test
    fun missingClipIsRejected() {
        val track = project().tracks.first { it.id == "visual-1" }

        assertThrows(IllegalArgumentException::class.java) {
            TimelineEditBounds.clampMove(track, "missing", 0)
        }
    }
}
