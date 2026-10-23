package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AssetType

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

        val preferred = when (selection) {
            EditorSelectionKind.PROJECT -> EditorToolId.MEDIA
            EditorSelectionKind.VIDEO, EditorSelectionKind.IMAGE -> EditorToolId.EDIT
            EditorSelectionKind.AUDIO -> EditorToolId.AUDIO
            EditorSelectionKind.TEXT -> EditorToolId.TEXT
            EditorSelectionKind.DESIGN -> EditorToolId.DESIGN
        }
        return available.firstOrNull { it.id == preferred }?.id ?: available.firstOrNull()?.id
    }
}
