package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph

fun LayerPanelUiPolicy.addNullParentForClip(
    graph: CompositionGraph,
    clipId: String,
): LayerGraphNodeCreation {
    require(clipId.isNotBlank()) { "clipId must not be blank" }
    val created = addNull(graph)
    return created.copy(
        graph = setClipParent(created.graph, clipId, created.nodeId),
    )
}

fun LayerPanelUiPolicy.addGroupParentForClip(
    graph: CompositionGraph,
    clipId: String,
): LayerGraphNodeCreation {
    require(clipId.isNotBlank()) { "clipId must not be blank" }
    val created = addGroup(graph)
    return created.copy(
        graph = setClipParent(created.graph, clipId, created.nodeId),
    )
}
