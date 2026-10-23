package com.localcut.editor.ui.editor

enum class EditorSelectionKind {
    PROJECT,
    VIDEO,
    IMAGE,
    AUDIO,
    TEXT,
    DESIGN,
}

enum class EditorToolGroup {
    PROJECT,
    EDIT,
    CREATIVE,
    TIME,
    AUDIO,
    DESIGN,
    ADVANCED,
}

enum class EditorToolState {
    LIVE,
    PARTIAL,
    PLANNED,
}

enum class EditorToolId {
    MEDIA,
    AUDIO,
    TEXT,
    OVERLAY,
    EDIT,
    EFFECTS,
    COLOR,
    SPEED,
    MASK,
    CHROMA,
    ANIMATION,
    LAYERS,
    DESIGN,
    CANVAS,
    CAPTIONS,
    TRACKING,
    STABILIZE,
    CAMERA,
    TEMPLATES,
}

data class EditorToolDefinition(
    val id: EditorToolId,
    val label: String,
    val group: EditorToolGroup,
    val selections: Set<EditorSelectionKind>,
    val state: EditorToolState,
    val primary: Boolean = true,
) {
    val enabled: Boolean get() = state == EditorToolState.LIVE
}

object EditorToolCatalog {
    private val project = setOf(EditorSelectionKind.PROJECT)
    private val visual = setOf(EditorSelectionKind.VIDEO, EditorSelectionKind.IMAGE)
    private val clip = visual + EditorSelectionKind.AUDIO
    private val any = EditorSelectionKind.entries.toSet()

    val all: List<EditorToolDefinition> = listOf(
        tool(EditorToolId.MEDIA, "Mídia", EditorToolGroup.PROJECT, any, EditorToolState.LIVE),
        tool(EditorToolId.AUDIO, "Áudio", EditorToolGroup.AUDIO, any, EditorToolState.LIVE),
        tool(EditorToolId.TEXT, "Texto", EditorToolGroup.PROJECT, any, EditorToolState.LIVE),
        tool(EditorToolId.OVERLAY, "Overlay", EditorToolGroup.PROJECT, any, EditorToolState.LIVE),
        tool(EditorToolId.EDIT, "Editar", EditorToolGroup.EDIT, clip, EditorToolState.LIVE),
        tool(EditorToolId.EFFECTS, "Efeitos", EditorToolGroup.CREATIVE, visual, EditorToolState.LIVE),
        tool(EditorToolId.COLOR, "Cor", EditorToolGroup.CREATIVE, visual, EditorToolState.LIVE),
        tool(EditorToolId.SPEED, "Velocidade", EditorToolGroup.TIME, setOf(EditorSelectionKind.VIDEO, EditorSelectionKind.AUDIO), EditorToolState.LIVE),
        tool(EditorToolId.MASK, "Máscara", EditorToolGroup.CREATIVE, visual, EditorToolState.LIVE),
        tool(EditorToolId.CHROMA, "Chroma", EditorToolGroup.CREATIVE, visual, EditorToolState.LIVE),
        tool(EditorToolId.ANIMATION, "Animação", EditorToolGroup.CREATIVE, visual + EditorSelectionKind.TEXT + EditorSelectionKind.DESIGN, EditorToolState.LIVE),
        tool(EditorToolId.LAYERS, "Camadas", EditorToolGroup.DESIGN, visual + EditorSelectionKind.TEXT + EditorSelectionKind.DESIGN, EditorToolState.LIVE),
        tool(EditorToolId.DESIGN, "Design", EditorToolGroup.DESIGN, project + EditorSelectionKind.DESIGN, EditorToolState.LIVE),
        tool(EditorToolId.CANVAS, "Canvas", EditorToolGroup.DESIGN, any, EditorToolState.LIVE),
        tool(EditorToolId.CAPTIONS, "Legendas", EditorToolGroup.PROJECT, project + EditorSelectionKind.TEXT, EditorToolState.PARTIAL),
        tool(EditorToolId.TRACKING, "Tracking", EditorToolGroup.ADVANCED, visual, EditorToolState.PLANNED),
        tool(EditorToolId.STABILIZE, "Estabilizar", EditorToolGroup.ADVANCED, setOf(EditorSelectionKind.VIDEO), EditorToolState.PLANNED),
        tool(EditorToolId.CAMERA, "Câmera", EditorToolGroup.ADVANCED, any, EditorToolState.PARTIAL),
        tool(EditorToolId.TEMPLATES, "Modelos", EditorToolGroup.DESIGN, project + EditorSelectionKind.DESIGN, EditorToolState.PARTIAL),
    )

    fun primaryToolsFor(selection: EditorSelectionKind): List<EditorToolDefinition> =
        all.filter { it.primary && selection in it.selections }

    fun toolsFor(selection: EditorSelectionKind, group: EditorToolGroup? = null): List<EditorToolDefinition> =
        all.filter { definition ->
            selection in definition.selections && (group == null || definition.group == group)
        }

    private fun tool(
        id: EditorToolId,
        label: String,
        group: EditorToolGroup,
        selections: Set<EditorSelectionKind>,
        state: EditorToolState,
        primary: Boolean = true,
    ): EditorToolDefinition = EditorToolDefinition(
        id = id,
        label = label,
        group = group,
        selections = selections,
        state = state,
        primary = primary,
    )
}
