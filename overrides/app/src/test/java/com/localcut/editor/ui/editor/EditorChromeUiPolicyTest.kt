package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AssetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun selectingAClipDoesNotOpenAContextPanelAutomatically() {
        assertNull(EditorChromeUiPolicy.resolveActiveTool(null, EditorSelectionKind.VIDEO))
        assertNull(EditorChromeUiPolicy.resolveActiveTool(null, EditorSelectionKind.IMAGE))
        assertNull(EditorChromeUiPolicy.resolveActiveTool(null, EditorSelectionKind.AUDIO))
    }

    @Test
    fun explicitlyOpenedToolIsPreservedWhenStillAvailable() {
        assertEquals(
            EditorToolId.MASK,
            EditorChromeUiPolicy.resolveActiveTool(EditorToolId.MASK, EditorSelectionKind.VIDEO),
        )
        assertEquals(
            EditorToolId.COLOR,
            EditorChromeUiPolicy.resolveActiveTool(EditorToolId.COLOR, EditorSelectionKind.IMAGE),
        )
        assertEquals(
            EditorToolId.AUDIO,
            EditorChromeUiPolicy.resolveActiveTool(EditorToolId.AUDIO, EditorSelectionKind.AUDIO),
        )
    }

    @Test
    fun creationActionsDoNotPinAContextPanel() {
        assertNull(EditorChromeUiPolicy.resolveActiveTool(EditorToolId.MEDIA, EditorSelectionKind.VIDEO))
        assertNull(EditorChromeUiPolicy.resolveActiveTool(EditorToolId.TEXT, EditorSelectionKind.VIDEO))
        assertNull(EditorChromeUiPolicy.resolveActiveTool(EditorToolId.OVERLAY, EditorSelectionKind.IMAGE))
    }

    @Test
    fun incompatibleToolClosesInsteadOfOpeningAnotherPanel() {
        assertNull(EditorChromeUiPolicy.resolveActiveTool(EditorToolId.MASK, EditorSelectionKind.AUDIO))
        assertNull(EditorChromeUiPolicy.resolveActiveTool(EditorToolId.SPEED, EditorSelectionKind.IMAGE))
        assertNull(EditorChromeUiPolicy.resolveActiveTool(EditorToolId.TRACKING, EditorSelectionKind.VIDEO))
    }

    @Test
    fun audioImportActionDoesNotPinAudioPanelForVisualSelection() {
        assertNull(EditorChromeUiPolicy.resolveActiveTool(EditorToolId.AUDIO, EditorSelectionKind.VIDEO))
    }

    @Test
    fun contextualToolsRouteToDedicatedPanels() {
        assertEquals(ContextPanelKind.EDIT, EditorChromeUiPolicy.panelFor(EditorToolId.EDIT))
        assertEquals(ContextPanelKind.AUDIO, EditorChromeUiPolicy.panelFor(EditorToolId.AUDIO))
        assertEquals(ContextPanelKind.EFFECTS, EditorChromeUiPolicy.panelFor(EditorToolId.EFFECTS))
        assertEquals(ContextPanelKind.COLOR, EditorChromeUiPolicy.panelFor(EditorToolId.COLOR))
        assertEquals(ContextPanelKind.SPEED, EditorChromeUiPolicy.panelFor(EditorToolId.SPEED))
        assertEquals(ContextPanelKind.MASK, EditorChromeUiPolicy.panelFor(EditorToolId.MASK))
        assertEquals(ContextPanelKind.CHROMA, EditorChromeUiPolicy.panelFor(EditorToolId.CHROMA))
        assertEquals(ContextPanelKind.ANIMATION, EditorChromeUiPolicy.panelFor(EditorToolId.ANIMATION))
        assertEquals(ContextPanelKind.LAYERS, EditorChromeUiPolicy.panelFor(EditorToolId.LAYERS))
        assertEquals(ContextPanelKind.DESIGN, EditorChromeUiPolicy.panelFor(EditorToolId.DESIGN))
        assertEquals(ContextPanelKind.CANVAS, EditorChromeUiPolicy.panelFor(EditorToolId.CANVAS))
        assertNull(EditorChromeUiPolicy.panelFor(EditorToolId.MEDIA))
        assertNull(EditorChromeUiPolicy.panelFor(EditorToolId.TEXT))
        assertNull(EditorChromeUiPolicy.panelFor(EditorToolId.OVERLAY))
    }
}
