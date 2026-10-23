package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AssetType

object EditorChromeUiPolicy {
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
        val preserved = available.firstOrNull { it.id == current }
        return preserved?.id ?: available.firstOrNull()?.id
    }
}
