package com.localcut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMainFlowPolicyTest {
    @Test
    fun cropIsLiveAndReachableForVisualSelections() {
        val crop = EditorToolCatalog.all.single { it.id == EditorToolId.CROP }

        assertTrue(crop.enabled)
        assertEquals(EditorToolState.LIVE, crop.state)
        assertEquals(
            setOf(EditorSelectionKind.VIDEO, EditorSelectionKind.IMAGE),
            crop.selections,
        )
        assertTrue(
            EditorToolCatalog.primaryToolsFor(EditorSelectionKind.VIDEO)
                .any { it.id == EditorToolId.CROP },
        )
        assertTrue(
            EditorToolCatalog.primaryToolsFor(EditorSelectionKind.IMAGE)
                .any { it.id == EditorToolId.CROP },
        )
    }

    @Test
    fun cropUsesItsOwnRailControlInsteadOfSharingChromaPanel() {
        assertNull(EditorChromeUiPolicy.panelFor(EditorToolId.CROP))
    }

    @Test
    fun cropIsReachableFromEditAndFxWorkspacesForVideo() {
        val editIds = EditorWorkspacePolicy.toolsFor(
            EditorWorkspace.EDIT,
            EditorSelectionKind.VIDEO,
        ).map { it.id }
        val fxIds = EditorWorkspacePolicy.toolsFor(
            EditorWorkspace.FX,
            EditorSelectionKind.VIDEO,
        ).map { it.id }

        assertTrue(EditorToolId.CROP in editIds)
        assertTrue(EditorToolId.CROP in fxIds)
    }
}
