package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AssetType
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorChromeUiPolicyTest {
    @Test
    fun assetTypeMapsToContextualSelectionKind() {
        assertEquals(EditorSelectionKind.PROJECT, EditorChromeUiPolicy.selectionForAsset(null))
        assertEquals(EditorSelectionKind.VIDEO, EditorChromeUiPolicy.selectionForAsset(AssetType.VIDEO))
        assertEquals(EditorSelectionKind.IMAGE, EditorChromeUiPolicy.selectionForAsset(AssetType.IMAGE))
        assertEquals(EditorSelectionKind.AUDIO, EditorChromeUiPolicy.selectionForAsset(AssetType.AUDIO))
    }

    @Test
    fun activeToolIsPreservedWhenStillAvailable() {
        assertEquals(
            EditorToolId.MASK,
            EditorChromeUiPolicy.resolveActiveTool(EditorToolId.MASK, EditorSelectionKind.VIDEO),
        )
    }

    @Test
    fun incompatibleToolFallsBackToFirstLiveToolForNewSelection() {
        assertEquals(
            EditorToolId.AUDIO,
            EditorChromeUiPolicy.resolveActiveTool(EditorToolId.MASK, EditorSelectionKind.AUDIO),
        )
    }

    @Test
    fun plannedToolCannotBecomeActive() {
        assertEquals(
            EditorToolId.EDIT,
            EditorChromeUiPolicy.resolveActiveTool(EditorToolId.TRACKING, EditorSelectionKind.VIDEO),
        )
    }
}
