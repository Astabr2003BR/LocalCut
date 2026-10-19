package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.*
import com.localcut.editor.session.EditorAction
import com.localcut.editor.domain.timeline.TimelineOps

private var passed = 0
private var failed = 0
private fun test(name: String, block: () -> Unit) {
    try { block(); println("PASS $name"); passed++ }
    catch (t: Throwable) { println("FAIL $name: ${t.message}"); failed++ }
}
private fun eq(expected: Any?, actual: Any?) { check(expected == actual) { "expected=$expected actual=$actual" } }

private fun project(): Project {
    val asset = Asset("a", AssetType.VIDEO, "content://a", 10_000_000, 1, "a.mp4", "video/mp4", "fp")
    val clip = Clip("c", "a", 1_000_000, 2_000_000, 0, 2_000_000)
    return Project.create("p", 1080, 1920, 30).copy(
        assets = listOf(asset),
        tracks = listOf(
            Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
            Track("audio-1", TrackType.AUDIO, 1)
        )
    )
}

fun main() {
    val p = project()
    val g = TimelineGeometry(100.0)

    test("body drag preserves grab offset") {
        val drag = TimelineDragPlanner.begin(p, "visual-1", 200.0, g, 12.0)!!
        eq(TimelineHitRegion.BODY, drag.region)
        eq(EditorAction.SelectClip("c"), drag.selectAction)
        eq(EditorAction.MoveSelected(2_000_000), TimelineDragPlanner.actionAt(drag, 300.0, g))
    }

    test("body drag clamps start at zero") {
        val drag = TimelineDragPlanner.begin(p, "visual-1", 200.0, g, 12.0)!!
        eq(EditorAction.MoveSelected(0), TimelineDragPlanner.actionAt(drag, 0.0, g))
    }

    test("left handle drag creates left edge trim action") {
        val drag = TimelineDragPlanner.begin(p, "visual-1", 105.0, g, 12.0)!!
        eq(TimelineHitRegion.LEFT_HANDLE, drag.region)
        eq(EditorAction.TrimSelectedLeftEdge(1_500_000), TimelineDragPlanner.actionAt(drag, 150.0, g))
    }

    test("right handle drag creates right edge trim action") {
        val drag = TimelineDragPlanner.begin(p, "visual-1", 295.0, g, 12.0)!!
        eq(TimelineHitRegion.RIGHT_HANDLE, drag.region)
        eq(EditorAction.TrimSelectedRightEdge(3_500_000), TimelineDragPlanner.actionAt(drag, 350.0, g))
    }

    test("touch outside any clip does not begin drag") {
        eq(null, TimelineDragPlanner.begin(p, "visual-1", 50.0, g, 12.0))
    }

    test("left trim cannot cross previous split clip") {
        val source = Project.create("split", 1080, 1920, 30).copy(
            assets = listOf(Asset("v", AssetType.VIDEO, "content://v", 5_000_000, 1, "v.mp4", "video/mp4", "fp")),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(Clip("c", "v", 0, 5_000_000, 0, 5_000_000))),
                Track("audio-1", TrackType.AUDIO, 1)
            )
        )
        val split = TimelineOps.splitClip(source, "c", 2_000_000)
        val drag = TimelineDragPlanner.begin(split, "visual-1", 205.0, g, 12.0)!!
        eq(TimelineHitRegion.LEFT_HANDLE, drag.region)
        eq(EditorAction.TrimSelectedLeftEdge(2_000_000), TimelineDragPlanner.actionAt(drag, 100.0, g))
    }

    test("right trim cannot cross next split clip") {
        val source = Project.create("split", 1080, 1920, 30).copy(
            assets = listOf(Asset("v", AssetType.VIDEO, "content://v", 5_000_000, 1, "v.mp4", "video/mp4", "fp")),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(Clip("c", "v", 0, 5_000_000, 0, 5_000_000))),
                Track("audio-1", TrackType.AUDIO, 1)
            )
        )
        val split = TimelineOps.splitClip(source, "c", 2_000_000)
        val drag = TimelineDragPlanner.begin(split, "visual-1", 195.0, g, 12.0)!!
        eq(TimelineHitRegion.RIGHT_HANDLE, drag.region)
        eq(EditorAction.TrimSelectedRightEdge(2_000_000), TimelineDragPlanner.actionAt(drag, 400.0, g))
    }

    println("RESULT passed=$passed failed=$failed")
    check(failed == 0) { "$failed tests failed" }
}
