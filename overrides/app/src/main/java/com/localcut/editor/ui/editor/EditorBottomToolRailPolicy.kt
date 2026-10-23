package com.localcut.editor.ui.editor

data class EditorBottomToolRailLayout(
    val visible: List<EditorToolDefinition>,
    val hiddenCount: Int,
) {
    val hasMore: Boolean get() = hiddenCount > 0
}

object EditorBottomToolRailPolicy {
    const val COLLAPSED_LIMIT = 6

    fun layout(
        tools: List<EditorToolDefinition>,
        expanded: Boolean,
        activeTool: EditorToolId? = null,
    ): EditorBottomToolRailLayout {
        if (expanded || tools.size <= COLLAPSED_LIMIT) {
            return EditorBottomToolRailLayout(
                visible = tools,
                hiddenCount = 0,
            )
        }

        val visible = tools.take(COLLAPSED_LIMIT).toMutableList()
        val active = tools.firstOrNull { it.id == activeTool }
        if (active != null && visible.none { it.id == active.id }) {
            visible[visible.lastIndex] = active
        }
        return EditorBottomToolRailLayout(
            visible = visible,
            hiddenCount = tools.size - visible.size,
        )
    }
}
