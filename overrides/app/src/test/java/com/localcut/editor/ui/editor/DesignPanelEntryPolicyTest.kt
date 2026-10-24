package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignPanelEntryPolicyTest {
    @Test
    fun designToolOpensElementsSection() {
        assertEquals(
            DesignToolsSection.ELEMENTS,
            DesignPanelEntryPolicy.initialSectionFor(EditorToolId.DESIGN),
        )
    }

    @Test
    fun canvasToolOpensCanvasSection() {
        assertEquals(
            DesignToolsSection.CANVAS,
            DesignPanelEntryPolicy.initialSectionFor(EditorToolId.CANVAS),
        )
    }
}
