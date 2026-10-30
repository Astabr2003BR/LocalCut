package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTrackingPreviewSessionTest {
    @Test
    fun disablingSessionClearsSelectionButKeepsLatestViewport() {
        ObjectTrackingPreviewSession.resetForTest()
        ObjectTrackingPreviewSession.setEnabled(true)
        ObjectTrackingPreviewSession.updateViewport(1000, 500)
        ObjectTrackingPreviewSession.setSelection(PreviewSelectionRect(100.0, 100.0, 200.0, 200.0))

        ObjectTrackingPreviewSession.setEnabled(false)

        val state = ObjectTrackingPreviewSession.state.value
        assertFalse(state.enabled)
        assertNull(state.selection)
        assertEquals(1000, state.viewportWidthPx)
        assertEquals(500, state.viewportHeightPx)
    }

    @Test
    fun viewportChangeInvalidatesCoordinatesFromPreviousSurface() {
        ObjectTrackingPreviewSession.resetForTest()
        ObjectTrackingPreviewSession.setEnabled(true)
        ObjectTrackingPreviewSession.updateViewport(1000, 500)
        ObjectTrackingPreviewSession.setSelection(PreviewSelectionRect(100.0, 100.0, 200.0, 200.0))

        ObjectTrackingPreviewSession.updateViewport(500, 1000)

        val state = ObjectTrackingPreviewSession.state.value
        assertTrue(state.enabled)
        assertNull(state.selection)
        assertEquals(500, state.viewportWidthPx)
        assertEquals(1000, state.viewportHeightPx)
    }

    @Test
    fun sameViewportPreservesSelectionAndInvalidViewportClearsIt() {
        ObjectTrackingPreviewSession.resetForTest()
        ObjectTrackingPreviewSession.setEnabled(true)
        ObjectTrackingPreviewSession.updateViewport(1000, 500)
        val selection = PreviewSelectionRect(100.0, 100.0, 200.0, 200.0)
        ObjectTrackingPreviewSession.setSelection(selection)

        ObjectTrackingPreviewSession.updateViewport(1000, 500)
        assertEquals(selection, ObjectTrackingPreviewSession.state.value.selection)

        ObjectTrackingPreviewSession.updateViewport(0, 0)
        val state = ObjectTrackingPreviewSession.state.value
        assertNull(state.selection)
        assertEquals(0, state.viewportWidthPx)
        assertEquals(0, state.viewportHeightPx)
    }
}
