package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.PathPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorEraserPolicyTest {
    @Test
    fun overlappingPathsSelectTopmostHit() {
        val gesture = listOf(PathPoint(0.1, 0.5), PathPoint(0.9, 0.5))
        val paths = listOf(
            VectorEraserCandidate("back", 1, listOf(PathPoint(0.2, 0.5), PathPoint(0.8, 0.5))),
            VectorEraserCandidate("front", 7, listOf(PathPoint(0.2, 0.5), PathPoint(0.8, 0.5))),
        )

        assertEquals("front", VectorEraserPolicy.findTopmostHit(paths, gesture, 0.02))
    }

    @Test
    fun crossingGestureHitsSegmentEvenWhenSamplePointsAreNotNearVertices() {
        val path = VectorEraserCandidate(
            elementId = "diagonal",
            zIndex = 2,
            points = listOf(PathPoint(0.1, 0.1), PathPoint(0.9, 0.9)),
        )
        val gesture = listOf(PathPoint(0.1, 0.9), PathPoint(0.9, 0.1))

        assertEquals("diagonal", VectorEraserPolicy.findTopmostHit(listOf(path), gesture, 0.01))
    }

    @Test
    fun distantGestureDoesNotDeleteAnything() {
        val path = VectorEraserCandidate(
            elementId = "stroke",
            zIndex = 1,
            points = listOf(PathPoint(0.1, 0.1), PathPoint(0.2, 0.2)),
        )
        val gesture = listOf(PathPoint(0.8, 0.8), PathPoint(0.9, 0.9))

        assertNull(VectorEraserPolicy.findTopmostHit(listOf(path), gesture, 0.03))
    }

    @Test
    fun invalidRadiusFailsClosed() {
        val gesture = listOf(PathPoint(0.0, 0.0), PathPoint(1.0, 1.0))
        assertTrue(runCatching { VectorEraserPolicy.findTopmostHit(emptyList(), gesture, 0.0) }.isFailure)
        assertTrue(runCatching { VectorEraserPolicy.findTopmostHit(emptyList(), gesture, 0.30) }.isFailure)
    }
}
