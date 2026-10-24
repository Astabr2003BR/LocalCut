package com.localcut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorWorkspacePolicyTest {
    @Test
    fun editWorkspaceKeepsCoreVideoEditingTools() {
        val ids = EditorWorkspacePolicy.toolsFor(
            workspace = EditorWorkspace.EDIT,
            selection = EditorSelectionKind.VIDEO,
        ).map { it.id }.toSet()

        assertTrue(EditorToolId.EDIT in ids)
        assertTrue(EditorToolId.EFFECTS in ids)
        assertTrue(EditorToolId.COLOR in ids)
        assertTrue(EditorToolId.SPEED in ids)
        assertFalse(EditorToolId.DESIGN in ids)
    }

    @Test
    fun motionWorkspacePrioritizesAnimationAndLayers() {
        val ids = EditorWorkspacePolicy.toolsFor(
            workspace = EditorWorkspace.MOTION,
            selection = EditorSelectionKind.VIDEO,
        ).map { it.id }

        assertTrue(ids.first() == EditorToolId.ANIMATION)
        assertTrue(EditorToolId.LAYERS in ids)
        assertTrue(EditorToolId.MASK in ids)
        assertFalse(EditorToolId.MEDIA in ids)
    }

    @Test
    fun designWorkspaceKeepsCreationAndCanvasToolsSeparate() {
        val ids = EditorWorkspacePolicy.toolsFor(
            workspace = EditorWorkspace.DESIGN,
            selection = EditorSelectionKind.PROJECT,
        ).map { it.id }.toSet()

        assertTrue(EditorToolId.DESIGN in ids)
        assertTrue(EditorToolId.CANVAS in ids)
        assertTrue(EditorToolId.TEXT in ids)
        assertTrue(EditorToolId.MEDIA in ids)
        assertFalse(EditorToolId.SPEED in ids)
    }

    @Test
    fun unavailableToolsRemainHiddenInEveryWorkspace() {
        EditorWorkspace.entries.forEach { workspace ->
            EditorSelectionKind.entries.forEach { selection ->
                assertTrue(
                    EditorWorkspacePolicy.toolsFor(workspace, selection).all { it.enabled },
                )
            }
        }
    }
}
