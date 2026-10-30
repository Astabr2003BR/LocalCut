package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorWorkspaceDrawTest {
    @Test
    fun drawWorkspaceIsExposedAndPrioritizesDedicatedDrawTool() {
        assertTrue(EditorWorkspace.entries.any { it == EditorWorkspace.DRAW && it.label == "Desenho" })

        val projectTools = EditorWorkspacePolicy.toolsFor(
            workspace = EditorWorkspace.DRAW,
            selection = EditorSelectionKind.PROJECT,
        ).map { it.id }

        assertEquals(EditorToolId.DRAW, projectTools.first())
        assertTrue(EditorToolId.DESIGN in projectTools)
        assertTrue(EditorToolId.CANVAS in projectTools)
        assertTrue(EditorToolId.TEXT in projectTools)
        assertTrue(projectTools.all { id -> EditorToolCatalog.all.first { it.id == id }.enabled })
    }

    @Test
    fun drawWorkspaceDoesNotExposePlannedFakeTools() {
        EditorSelectionKind.entries.forEach { selection ->
            val definitions = EditorWorkspacePolicy.toolsFor(EditorWorkspace.DRAW, selection)
            assertTrue(definitions.none { it.state != EditorToolState.LIVE })
        }
    }
}
