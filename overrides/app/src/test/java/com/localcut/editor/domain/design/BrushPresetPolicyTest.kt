package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushPresetPolicyTest {
    @Test
    fun presetsStayOrderedAndMobileFriendly() {
        assertEquals(
            listOf("fineliner", "pencil", "ink", "marker", "highlighter", "bold"),
            BrushPresetPolicy.presets().map { it.id },
        )
        assertTrue(BrushPresetPolicy.presets().all { it.strokeWidthPx in 0.5..48.0 })
        assertTrue(BrushPresetPolicy.presets().all { it.opacity in 0.0..1.0 })
        assertEquals(
            BrushPresetPolicy.presets().size,
            BrushPresetPolicy.presets().map { it.id }.distinct().size,
        )
    }

    @Test
    fun presetLookupReturnsExpectedStyle() {
        val fineliner = BrushPresetPolicy.byId("fineliner")
        val pencil = BrushPresetPolicy.byId("pencil")
        val marker = BrushPresetPolicy.byId("marker")
        val highlighter = BrushPresetPolicy.byId("highlighter")

        assertTrue(fineliner.strokeWidthPx < pencil.strokeWidthPx)
        assertEquals(1.0, fineliner.opacity, 0.0)
        assertEquals(4.0, pencil.strokeWidthPx, 0.0)
        assertTrue(marker.strokeWidthPx > pencil.strokeWidthPx)
        assertTrue(marker.opacity < 1.0)
        assertTrue(highlighter.strokeWidthPx > marker.strokeWidthPx)
        assertTrue(highlighter.opacity < marker.opacity)
        assertTrue(runCatching { BrushPresetPolicy.byId("unknown") }.isFailure)
    }
}
