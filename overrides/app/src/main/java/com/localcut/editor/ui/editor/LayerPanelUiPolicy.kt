package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind

object LayerPanelUiPolicy {
    fun isClipVisible(graph: CompositionGraph, clipId: String): Boolean =
        graph.nodes.firstOrNull { it.kind == CompositionNodeKind.LAYER && it.clipId == clipId }?.visible ?: true

    fun setClipVisible(
        graph: CompositionGraph,
        clipId: String,
        visible: Boolean,
    ): CompositionGraph {
        require(clipId.isNotBlank()) { "clipId must not be blank" }
        val existingIndex = graph.nodes.indexOfFirst {
            it.kind == CompositionNodeKind.LAYER && it.clipId == clipId
        }
        if (existingIndex >= 0) {
            val nodes = graph.nodes.toMutableList()
            nodes[existingIndex] = nodes[existingIndex].copy(visible = visible)
            return graph.copy(nodes = nodes)
        }

        val nodeId = nextLayerNodeId(graph, clipId)
        return graph.copy(
            nodes = graph.nodes + CompositionNode(
                id = nodeId,
                kind = CompositionNodeKind.LAYER,
                clipId = clipId,
                visible = visible,
            ),
        )
    }

    private fun nextLayerNodeId(graph: CompositionGraph, clipId: String): String {
        val base = "layer-$clipId"
        val occupied = graph.nodes.map { it.id }.toHashSet()
        if (base !in occupied) return base
        var suffix = 2L
        while (true) {
            val candidate = "$base-$suffix"
            if (candidate !in occupied) return candidate
            check(suffix < Long.MAX_VALUE) { "unable to allocate layer node id" }
            suffix++
        }
    }
}
