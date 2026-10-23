package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorBottomToolRailPolicyTest {
    @Test
    fun collapsedRailShowsAtMostSixTools() {
        val tools = EditorToolCatalog.primaryToolsFor(EditorSelectionKind.VIDEO).filter { it.enabled }
        val layout = EditorBottomToolRailPolicy.layout(tools, expanded = false)

        assertEquals(6, layout.visible.size)
        assertTrue(layout.hiddenCount > 0)
        assertTrue(layout.hasMore)
    }

    @Test
    fun expandedRailShowsEveryEnabledTool() {
        val tools = EditorToolCatalog.primaryToolsFor(EditorSelectionKind.VIDEO).filter { it.enabled }
        val layout = EditorBottomToolRailPolicy.layout(tools, expanded = true)

        assertEquals(tools.map { it.id }, layout.visible.map { it.id })
        assertEquals(0, layout.hiddenCount)
        assertFalse(layout.hasMore)
    }

    @Test
    fun shortRailDoesNotOfferMore() {
        val tools = EditorToolCatalog.primaryToolsFor(EditorSelectionKind.AUDIO)
            .filter { it.enabled }
            .take(3)
        val layout = EditorBottomToolRailPolicy.layout(tools, expanded = false)

        assertEquals(tools.map { it.id }, layout.visible.map { it.id })
        assertEquals(0, layout.hiddenCount)
        assertFalse(layout.hasMore)
    }
}
