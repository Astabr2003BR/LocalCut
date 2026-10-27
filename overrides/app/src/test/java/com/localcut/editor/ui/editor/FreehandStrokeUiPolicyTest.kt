package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreehandStrokeUiPolicyTest {
    @Test
    fun createsNormalizedTimedStrokeWithStableVisualWidth() {
        val element = FreehandStrokeUiPolicy.createStroke(
            projectWidth = 1000,
            projectHeight = 500,
            existingIds = setOf("stroke-1"),
            maxZIndex = 4,
            playheadUs = 2_000_000L,
            points = listOf(PathPoint(0.1, 0.2), PathPoint(0.9, 0.8)),
            colorArgb = 0xFFFFFFFF.toInt(),
            brushWidthPx = 20.0,
        )

        assertEquals("stroke-2", element.id)
        assertEquals(2_000_000L, element.startUs)
        assertEquals(FreehandStrokeUiPolicy.DEFAULT_DURATION_US, element.durationUs)
        assertEquals(5, element.zIndex)
        assertEquals(0.0, element.transform.x, 0.0)
        assertEquals(0.0, element.transform.y, 0.0)
        assertEquals(6.4, element.transform.scale, 1e-9)
        assertEquals(PathPoint(0.0, 0.3125), element.shape!!.path!!.points.first())
        assertEquals(PathPoint(1.0, 0.6875), element.shape!!.path!!.points.last())
        assertEquals(3.125, element.shape!!.strokeWidthPx, 1e-9)
        assertTrue(FreehandStrokeUiPolicy.isFreehandStroke(element))
        assertTrue(element.validationIssues().isEmpty())
    }

    @Test
    fun boundsLongGestureToProtocolPointBudgetWhilePreservingEndpoints() {
        val points = (0..600).map { index -> PathPoint(index / 600.0, index / 1200.0) }
        val element = FreehandStrokeUiPolicy.createStroke(
            projectWidth = 1080,
            projectHeight = 1920,
            existingIds = emptySet(),
            maxZIndex = null,
            playheadUs = 0L,
            points = points,
            colorArgb = 0xFF000000.toInt(),
            brushWidthPx = 8.0,
        )

        val sampled = element.shape!!.path!!.points
        assertEquals(FreehandStrokeUiPolicy.MAX_POINTS, sampled.size)
        assertEquals(0.0, sampled.first().x, 1e-9)
        assertEquals(1.0, sampled.last().x, 1e-9)
    }

    @Test
    fun rejectsInvalidGestureAndClosedPathsAreNotFreehandStrokes() {
        val failure = runCatching {
            FreehandStrokeUiPolicy.createStroke(
                projectWidth = 1080,
                projectHeight = 1920,
                existingIds = emptySet(),
                maxZIndex = null,
                playheadUs = 0L,
                points = listOf(PathPoint(0.5, 0.5)),
                colorArgb = 0xFFFFFFFF.toInt(),
                brushWidthPx = 12.0,
            )
        }
        assertTrue(failure.isFailure)

        val closed = DesignElement(
            id = "shape-1",
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(
                    points = listOf(PathPoint(0.0, 0.0), PathPoint(1.0, 1.0)),
                    closed = true,
                ),
            ),
        )
        assertFalse(FreehandStrokeUiPolicy.isFreehandStroke(closed))
    }
}
