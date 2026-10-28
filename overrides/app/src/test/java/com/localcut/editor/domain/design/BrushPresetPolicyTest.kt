package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushPresetPolicyTest {
    @Test
    fun presetsStayOrderedAndMobileFriendly() {
        assertEquals(
            listOf("pencil", "ink", "marker", "bold"),
            BrushPresetPolicy.presets().map { it.id },
        )
        assertTrue(BrushPresetPolicy.presets().all { it.strokeWidthPx in 0.5..48.0 })
        assertTrue(BrushPresetPolicy.presets().all { it.opacity in 0.0..1.0 })
    }

    @Test
    fun presetLookupReturnsExpectedStyle() {
        val pencil = BrushPresetPolicy.byId("pencil")
        val marker = BrushPresetPolicy.byId("marker")

        assertEquals(4.0, pencil.strokeWidthPx, 0.0)
        assertTrue(marker.strokeWidthPx > pencil.strokeWidthPx)
        assertTrue(marker.opacity < 1.0)
        assertTrue(runCatching { BrushPresetPolicy.byId("unknown") }.isFailure)
    }
}
