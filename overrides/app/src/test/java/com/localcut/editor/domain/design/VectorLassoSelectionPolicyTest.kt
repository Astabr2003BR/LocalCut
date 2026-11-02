package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorLassoSelectionPolicyTest {
    @Test
    fun selectsVisiblePathWhenCenterlineFallsInsideLasso() {
        val element = pathElement(
            id = "inside",
            points = listOf(PathPoint(0.35, 0.5), PathPoint(0.65, 0.5)),
        )
        val selected = VectorLassoSelectionPolicy.selectPathIds(
            elements = listOf(element),
            lassoPointsPx = rectangle(400.0, 850.0, 680.0, 1_070.0),
            canvasWidthPx = 1080.0,
            canvasHeightPx = 1920.0,
            playheadUs = 500_000L,
        )

        assertEquals(listOf("inside"), selected)
    }

    @Test
    fun crossingSegmentIsSelectedEvenWhenItsVerticesAreOutsideLasso() {
        val element = pathElement(
            id = "crossing",
            points = listOf(PathPoint(0.0, 0.5), PathPoint(1.0, 0.5)),
        )
        val selected = VectorLassoSelectionPolicy.selectPathIds(
            elements = listOf(element),
            lassoPointsPx = rectangle(520.0, 930.0, 560.0, 990.0),
            canvasWidthPx = 1080.0,
            canvasHeightPx = 1920.0,
            playheadUs = 500_000L,
        )

        assertEquals(listOf("crossing"), selected)
    }

    @Test
    fun transformedPathUsesSameScaleRotationAndTranslationSemanticsAsRenderer() {
        val moved = pathElement(
            id = "moved",
            points = listOf(PathPoint(0.4, 0.5), PathPoint(0.6, 0.5)),
            transform = TransformValue(x = 250.0, y = -120.0, scale = 1.5, rotationDeg = 90.0),
        )
        val selected = VectorLassoSelectionPolicy.selectPathIds(
            elements = listOf(moved),
            lassoPointsPx = rectangle(730.0, 760.0, 850.0, 1_010.0),
            canvasWidthPx = 1080.0,
            canvasHeightPx = 1920.0,
            playheadUs = 500_000L,
        )

        assertEquals(listOf("moved"), selected)
    }

    @Test
    fun inactiveAndNonPathElementsAreIgnoredAndOrderingIsStableByZThenId() {
        val activeTop = pathElement("b", listOf(PathPoint(0.4, 0.5), PathPoint(0.6, 0.5)), zIndex = 3)
        val activeBottom = pathElement("a", listOf(PathPoint(0.4, 0.5), PathPoint(0.6, 0.5)), zIndex = 1)
        val inactive = pathElement(
            "inactive",
            listOf(PathPoint(0.4, 0.5), PathPoint(0.6, 0.5)),
            startUs = 2_000_000L,
        )
        val nonPath = DesignElement(
            id = "rect",
            startUs = 0L,
            durationUs = 2_000_000L,
            shape = ShapeSpec(kind = ShapeKind.RECTANGLE),
        )

        val selected = VectorLassoSelectionPolicy.selectPathIds(
            elements = listOf(activeTop, nonPath, inactive, activeBottom),
            lassoPointsPx = rectangle(400.0, 850.0, 680.0, 1_070.0),
            canvasWidthPx = 1080.0,
            canvasHeightPx = 1920.0,
            playheadUs = 500_000L,
        )

        assertEquals(listOf("a", "b"), selected)
    }

    @Test
    fun invalidLassoOrCanvasFailsClosed() {
        val element = pathElement("path", listOf(PathPoint(0.4, 0.5), PathPoint(0.6, 0.5)))
        assertTrue(runCatching {
            VectorLassoSelectionPolicy.selectPathIds(
                listOf(element),
                listOf(CanvasPointPx(1.0, 1.0), CanvasPointPx(2.0, 2.0)),
                1080.0,
                1920.0,
                0L,
            )
        }.isFailure)
        assertTrue(runCatching {
            VectorLassoSelectionPolicy.selectPathIds(
                listOf(element),
                rectangle(0.0, 0.0, 10.0, 10.0),
                0.0,
                1920.0,
                0L,
            )
        }.isFailure)
    }

    private fun pathElement(
        id: String,
        points: List<PathPoint>,
        transform: TransformValue = TransformValue(),
        startUs: Long = 0L,
        durationUs: Long = 1_000_000L,
        zIndex: Int = 0,
    ) = DesignElement(
        id = id,
        startUs = startUs,
        durationUs = durationUs,
        shape = ShapeSpec(
            kind = ShapeKind.PATH,
            path = VectorPath(points = points),
            fillArgb = null,
            strokeArgb = 0xFFFFFFFF.toInt(),
            strokeWidthPx = 4.0,
        ),
        transform = transform,
        zIndex = zIndex,
    )

    private fun rectangle(left: Double, top: Double, right: Double, bottom: Double) = listOf(
        CanvasPointPx(left, top),
        CanvasPointPx(right, top),
        CanvasPointPx(right, bottom),
        CanvasPointPx(left, bottom),
    )
}
