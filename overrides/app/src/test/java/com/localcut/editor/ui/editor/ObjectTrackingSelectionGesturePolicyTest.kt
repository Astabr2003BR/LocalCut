package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObjectTrackingSelectionGesturePolicyTest {
    @Test
    fun dragInAnyDirectionProducesCanonicalBoundedRect() {
        assertEquals(
            PreviewSelectionRect(100.0, 200.0, 800.0, 900.0),
            ObjectTrackingSelectionGesturePolicy.rect(
                startX = 800.0,
                startY = 900.0,
                currentX = 100.0,
                currentY = 200.0,
                viewportWidthPx = 1000.0,
                viewportHeightPx = 1000.0,
            ),
        )
    }

    @Test
    fun pointerOutsideCanvasIsClampedToViewport() {
        assertEquals(
            PreviewSelectionRect(0.0, 0.0, 1000.0, 1000.0),
            ObjectTrackingSelectionGesturePolicy.rect(
                startX = -100.0,
                startY = -50.0,
                currentX = 1_100.0,
                currentY = 1_200.0,
                viewportWidthPx = 1000.0,
                viewportHeightPx = 1000.0,
            ),
        )
    }

    @Test
    fun tapOrSubpixelDragDoesNotCreateSelection() {
        assertNull(
            ObjectTrackingSelectionGesturePolicy.rect(
                startX = 100.0,
                startY = 100.0,
                currentX = 100.5,
                currentY = 100.5,
                viewportWidthPx = 1000.0,
                viewportHeightPx = 1000.0,
            ),
        )
    }
}
