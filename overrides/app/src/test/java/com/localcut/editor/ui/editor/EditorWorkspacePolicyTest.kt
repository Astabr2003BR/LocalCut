package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorWorkspacePolicyTest {
    @Test
    fun workspaceLabelsExposeExpandedStudioNavigation() {
        assertEquals(
            listOf("Editar", "Animar", "Design", "Desenho", "Áudio", "FX", "Projeto"),
            EditorWorkspace.entries.map { it.label },
        )
    }

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
    fun animationWorkspacePrioritizesAnimationAndLayers() {
        val ids = EditorWorkspacePolicy.toolsFor(
            workspace = EditorWorkspace.MOTION,
            selection = EditorSelectionKind.VIDEO,
        ).map { it.id }

        assertEquals(EditorToolId.ANIMATION, ids.first())
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
    fun audioWorkspacePrioritizesAudioAndTimeTools() {
        val ids = EditorWorkspacePolicy.toolsFor(
            workspace = EditorWorkspace.AUDIO,
            selection = EditorSelectionKind.AUDIO,
        ).map { it.id }

        assertEquals(EditorToolId.AUDIO, ids.first())
        assertTrue(EditorToolId.SPEED in ids)
        assertTrue(EditorToolId.EDIT in ids)
        assertFalse(EditorToolId.EFFECTS in ids)
    }

    @Test
    fun fxWorkspacePrioritizesCreativeVisualTools() {
        val ids = EditorWorkspacePolicy.toolsFor(
            workspace = EditorWorkspace.FX,
            selection = EditorSelectionKind.VIDEO,
        ).map { it.id }

        assertEquals(EditorToolId.EFFECTS, ids.first())
        assertTrue(EditorToolId.COLOR in ids)
        assertTrue(EditorToolId.MASK in ids)
        assertTrue(EditorToolId.CHROMA in ids)
        assertFalse(EditorToolId.MEDIA in ids)
    }

    @Test
    fun projectWorkspaceUsesOnlyLiveProjectTools() {
        val ids = EditorWorkspacePolicy.toolsFor(
            workspace = EditorWorkspace.PROJECT,
            selection = EditorSelectionKind.PROJECT,
        ).map { it.id }

        assertEquals(EditorToolId.MEDIA, ids.first())
        assertTrue(EditorToolId.CANVAS in ids)
        assertTrue(EditorToolId.TEXT in ids)
        assertTrue(EditorToolId.AUDIO in ids)
        assertFalse(EditorToolId.TEMPLATES in ids)
        assertFalse(EditorToolId.CAMERA in ids)
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

    @Test
    fun switchingWorkspaceClosesContextPanelEvenWhenToolExistsInBothWorkspaces() {
        assertNull(
            EditorWorkspacePolicy.activeToolAfterWorkspaceChange(
                currentWorkspace = EditorWorkspace.MOTION,
                nextWorkspace = EditorWorkspace.DESIGN,
                activeTool = EditorToolId.COLOR,
            ),
        )
    }

    @Test
    fun selectingCurrentWorkspaceKeepsContextPanel() {
        assertEquals(
            EditorToolId.COLOR,
            EditorWorkspacePolicy.activeToolAfterWorkspaceChange(
                currentWorkspace = EditorWorkspace.MOTION,
                nextWorkspace = EditorWorkspace.MOTION,
                activeTool = EditorToolId.COLOR,
            ),
        )
    }
}
