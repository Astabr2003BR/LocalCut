package com.localcut.editor.ui.editor

enum class EditorWorkspace(val label: String) {
    EDIT("Editar"),
    MOTION("Animar"),
    DESIGN("Design"),
    DRAW("Desenho"),
    AUDIO("Áudio"),
    FX("FX"),
    PROJECT("Projeto"),
}

object EditorWorkspacePolicy {
    private val priorities = mapOf(
        EditorWorkspace.EDIT to listOf(
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
        ),
        EditorWorkspace.MOTION to listOf(
            EditorToolId.ANIMATION,
            EditorToolId.CAMERA,
            EditorToolId.LAYERS,
            EditorToolId.MASK,
            EditorToolId.EFFECTS,
            EditorToolId.EDIT,
            EditorToolId.COLOR,
            EditorToolId.SPEED,
            EditorToolId.CHROMA,
            EditorToolId.TEXT,
            EditorToolId.OVERLAY,
            EditorToolId.AUDIO,
            EditorToolId.CANVAS,
        ),
        EditorWorkspace.DESIGN to listOf(
            EditorToolId.DESIGN,
            EditorToolId.CANVAS,
            EditorToolId.TEXT,
            EditorToolId.MEDIA,
            EditorToolId.OVERLAY,
            EditorToolId.LAYERS,
            EditorToolId.ANIMATION,
            EditorToolId.EFFECTS,
            EditorToolId.COLOR,
        ),
        EditorWorkspace.DRAW to listOf(
            EditorToolId.DRAW,
            EditorToolId.DESIGN,
            EditorToolId.LAYERS,
            EditorToolId.ANIMATION,
            EditorToolId.CANVAS,
            EditorToolId.TEXT,
            EditorToolId.MEDIA,
            EditorToolId.OVERLAY,
            EditorToolId.MASK,
            EditorToolId.COLOR,
            EditorToolId.EFFECTS,
        ),
        EditorWorkspace.AUDIO to listOf(
            EditorToolId.AUDIO,
            EditorToolId.SPEED,
            EditorToolId.EDIT,
            EditorToolId.MEDIA,
            EditorToolId.TEXT,
            EditorToolId.CAPTIONS,
            EditorToolId.OVERLAY,
            EditorToolId.CANVAS,
        ),
        EditorWorkspace.FX to listOf(
            EditorToolId.EFFECTS,
            EditorToolId.COLOR,
            EditorToolId.MASK,
            EditorToolId.CHROMA,
            EditorToolId.LAYERS,
            EditorToolId.ANIMATION,
            EditorToolId.EDIT,
        ),
        EditorWorkspace.PROJECT to listOf(
            EditorToolId.MEDIA,
            EditorToolId.CANVAS,
            EditorToolId.TEXT,
            EditorToolId.AUDIO,
            EditorToolId.OVERLAY,
            EditorToolId.CAPTIONS,
            EditorToolId.DESIGN,
            EditorToolId.TEMPLATES,
        ),
    )

    fun toolsFor(
        workspace: EditorWorkspace,
        selection: EditorSelectionKind,
    ): List<EditorToolDefinition> {
        val priority = priorities.getValue(workspace)
        val rank = priority.withIndex().associate { (index, id) -> id to index }

        return EditorToolCatalog.all
            .asSequence()
            .filter { definition ->
                definition.primary &&
                    definition.enabled &&
                    definition.id in rank &&
                    selection in definition.selections
            }
            .sortedWith(
                compareBy<EditorToolDefinition> { rank[it.id] ?: Int.MAX_VALUE }
                    .thenBy { EditorToolCatalog.all.indexOf(it) },
            )
            .toList()
    }

    fun activeToolAfterWorkspaceChange(
        currentWorkspace: EditorWorkspace,
        nextWorkspace: EditorWorkspace,
        activeTool: EditorToolId?,
    ): EditorToolId? = if (currentWorkspace == nextWorkspace) activeTool else null
}
