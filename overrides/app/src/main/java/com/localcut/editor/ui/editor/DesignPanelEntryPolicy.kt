package com.localcut.editor.ui.editor

object DesignPanelEntryPolicy {
    fun initialSectionFor(tool: EditorToolId): DesignToolsSection = when (tool) {
        EditorToolId.CANVAS -> DesignToolsSection.CANVAS
        else -> DesignToolsSection.ELEMENTS
    }
}
