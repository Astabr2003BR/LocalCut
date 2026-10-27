package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawStrokePadPolicyTest {
    @Test
    fun allocatesStableStrokeIds() {
        assertEquals("stroke-1", DrawStrokePadPolicy.nextStrokeId(emptySet()))
        assertEquals(
            "stroke-3",
            DrawStrokePadPolicy.nextStrokeId(setOf("stroke-1", "stroke-2", "shape-1")),
        )
    }

    @Test
    fun allocatesNextZIndex() {
        assertEquals(0, DrawStrokePadPolicy.nextZIndex(emptyList()))
        assertEquals(5, DrawStrokePadPolicy.nextZIndex(listOf(-2, 4, 1)))
    }

    @Test
    fun defaultsStayInsideDrawCoreLimits() {
        assertEquals(3_000_000L, DrawStrokePadPolicy.DEFAULT_DURATION_US)
        assertTrue(DrawStrokePadPolicy.DEFAULT_STROKE_WIDTH_PX in 0.5..256.0)
    }
}
