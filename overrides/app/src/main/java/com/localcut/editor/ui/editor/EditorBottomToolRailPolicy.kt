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
    ): EditorBottomToolRailLayout {
        if (expanded || tools.size <= COLLAPSED_LIMIT) {
            return EditorBottomToolRailLayout(
                visible = tools,
                hiddenCount = 0,
            )
        }
        return EditorBottomToolRailLayout(
            visible = tools.take(COLLAPSED_LIMIT),
            hiddenCount = tools.size - COLLAPSED_LIMIT,
        )
    }
}
