package com.localcut.editor.ui.editor

enum class EditorWorkspace(val label: String) {
    EDIT("Editar"),
    MOTION("Motion"),
    DESIGN("Design"),
}

object EditorWorkspacePolicy {
    private val editTools = setOf(
        EditorToolId.EDIT,
        EditorToolId.MEDIA,
        EditorToolId.AUDIO,
        EditorToolId.TEXT,
        EditorToolId.CAPTIONS,
        EditorToolId.OVERLAY,
        EditorToolId.EFFECTS,
        EditorToolId.COLOR,
        EditorToolId.SPEED,
        EditorToolId.MASK,
        EditorToolId.CHROMA,
        EditorToolId.CANVAS,
    )

    private val motionTools = setOf(
        EditorToolId.ANIMATION,
        EditorToolId.LAYERS,
        EditorToolId.MASK,
        EditorToolId.EFFECTS,
        EditorToolId.EDIT,
        EditorToolId.COLOR,
        EditorToolId.SPEED,
        EditorToolId.CHROMA,
        EditorToolId.TEXT,
        EditorToolId.OVERLAY,
        EditorToolId.MEDIA,
        EditorToolId.AUDIO,
        EditorToolId.CANVAS,
    )

    private val designTools = setOf(
        EditorToolId.DESIGN,
        EditorToolId.CANVAS,
        EditorToolId.TEXT,
        EditorToolId.MEDIA,
        EditorToolId.OVERLAY,
        EditorToolId.LAYERS,
        EditorToolId.ANIMATION,
        EditorToolId.EFFECTS,
        EditorToolId.COLOR,
    )

    private val editPriority = listOf(
        EditorToolId.EDIT,
        EditorToolId.MEDIA,
        EditorToolId.AUDIO,
        EditorToolId.TEXT,
        EditorToolId.CAPTIONS,
        EditorToolId.OVERLAY,
        EditorToolId.EFFECTS,
        EditorToolId.COLOR,
        EditorToolId.SPEED,
        EditorToolId.MASK,
        EditorToolId.CHROMA,
        EditorToolId.CANVAS,
    )

    private val motionPriority = listOf(
        EditorToolId.ANIMATION,
        EditorToolId.LAYERS,
        EditorToolId.MASK,
        EditorToolId.EFFECTS,
        EditorToolId.EDIT,
        EditorToolId.COLOR,
        EditorToolId.SPEED,
        EditorToolId.CHROMA,
        EditorToolId.TEXT,
        EditorToolId.OVERLAY,
        EditorToolId.MEDIA,
        EditorToolId.AUDIO,
        EditorToolId.CANVAS,
    )

    private val designPriority = listOf(
        EditorToolId.DESIGN,
        EditorToolId.CANVAS,
        EditorToolId.TEXT,
        EditorToolId.MEDIA,
        EditorToolId.OVERLAY,
        EditorToolId.LAYERS,
        EditorToolId.ANIMATION,
        EditorToolId.EFFECTS,
        EditorToolId.COLOR,
    )

    fun toolsFor(
        workspace: EditorWorkspace,
        selection: EditorSelectionKind,
    ): List<EditorToolDefinition> {
        val allowed = when (workspace) {
            EditorWorkspace.EDIT -> editTools
            EditorWorkspace.MOTION -> motionTools
            EditorWorkspace.DESIGN -> designTools
        }
        val priority = when (workspace) {
            EditorWorkspace.EDIT -> editPriority
            EditorWorkspace.MOTION -> motionPriority
            EditorWorkspace.DESIGN -> designPriority
        }
        val rank = priority.withIndex().associate { (index, id) -> id to index }

        return EditorToolCatalog.all
            .asSequence()
            .filter { definition ->
                definition.primary &&
                    definition.enabled &&
                    definition.id in allowed &&
                    selection in definition.selections
            }
            .sortedWith(
                compareBy<EditorToolDefinition> { rank[it.id] ?: Int.MAX_VALUE }
                    .thenBy { EditorToolCatalog.all.indexOf(it) },
            )
            .toList()
    }
}
