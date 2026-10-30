package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AssetType

enum class ContextPanelKind {
    EDIT,
    AUDIO,
    EFFECTS,
    COLOR,
    SPEED,
    MASK,
    CHROMA,
    ANIMATION,
    TRACKING,
    LAYERS,
    DESIGN,
    CANVAS,
}

object EditorChromeUiPolicy {
    private val transientCreationTools = setOf(
        EditorToolId.MEDIA,
        EditorToolId.TEXT,
        EditorToolId.OVERLAY,
    )

    fun selectionForAsset(assetType: AssetType?): EditorSelectionKind = when (assetType) {
        null -> EditorSelectionKind.PROJECT
        AssetType.VIDEO -> EditorSelectionKind.VIDEO
        AssetType.IMAGE -> EditorSelectionKind.IMAGE
        AssetType.AUDIO -> EditorSelectionKind.AUDIO
    }

    fun resolveActiveTool(
        current: EditorToolId?,
        selection: EditorSelectionKind,
    ): EditorToolId? {
        val available = EditorToolCatalog.primaryToolsFor(selection).filter { it.enabled }
        val transient = current in transientCreationTools ||
            (current == EditorToolId.AUDIO && selection != EditorSelectionKind.AUDIO)

        if (!transient) {
            val preserved = available.firstOrNull { it.id == current }
            if (preserved != null) return preserved.id
        }

        // Mobile editor UX: selecting a clip must not automatically open a large
        // inspector. Keep the canvas/timeline clean and let the user explicitly
        // open Edit, Audio, Effects, Color, Speed, Mask, etc. from the bottom rail.
        return null
    }

    fun panelFor(tool: EditorToolId?): ContextPanelKind? = when (tool) {
        EditorToolId.EDIT -> ContextPanelKind.EDIT
        EditorToolId.AUDIO -> ContextPanelKind.AUDIO
        EditorToolId.EFFECTS -> ContextPanelKind.EFFECTS
        EditorToolId.COLOR -> ContextPanelKind.COLOR
        EditorToolId.SPEED -> ContextPanelKind.SPEED
        EditorToolId.MASK -> ContextPanelKind.MASK
        EditorToolId.CHROMA -> ContextPanelKind.CHROMA
        EditorToolId.ANIMATION -> ContextPanelKind.ANIMATION
        EditorToolId.TRACKING -> ContextPanelKind.TRACKING
        EditorToolId.LAYERS -> ContextPanelKind.LAYERS
        EditorToolId.DESIGN -> ContextPanelKind.DESIGN
        EditorToolId.DRAW -> ContextPanelKind.DESIGN
        EditorToolId.CANVAS -> ContextPanelKind.CANVAS
        else -> null
    }
}
