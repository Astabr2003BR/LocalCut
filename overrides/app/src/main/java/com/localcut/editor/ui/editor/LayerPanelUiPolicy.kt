package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind

data class LayerGraphNodeCreation(
    val graph: CompositionGraph,
    val nodeId: String,
)

object LayerPanelUiPolicy {
    fun isClipVisible(graph: CompositionGraph, clipId: String): Boolean =
        layerNode(graph, clipId)?.visible ?: true

    fun isClipLocked(graph: CompositionGraph, clipId: String): Boolean =
        layerNode(graph, clipId)?.locked ?: false

    fun layerParentId(graph: CompositionGraph, clipId: String): String? =
        layerNode(graph, clipId)?.parentId

    fun parentTargets(graph: CompositionGraph): List<CompositionNode> =
        graph.nodes.filter { it.kind == CompositionNodeKind.NULL || it.kind == CompositionNodeKind.GROUP }

    fun addNull(graph: CompositionGraph): LayerGraphNodeCreation {
        val nodeId = nextSequentialNodeId(graph, "null")
        return LayerGraphNodeCreation(
            graph = graph.copy(
                nodes = graph.nodes + CompositionNode(
                    id = nodeId,
                    kind = CompositionNodeKind.NULL,
                ),
            ),
            nodeId = nodeId,
        )
    }

    fun addStaticCamera(graph: CompositionGraph): LayerGraphNodeCreation {
        require(graph.nodes.none { it.kind == CompositionNodeKind.CAMERA }) {
            "a static camera already exists"
        }
        require(graph.cameraCuts.isEmpty()) {
            "camera cuts must be removed before creating the static camera"
        }
        val nodeId = nextSequentialNodeId(graph, "camera")
        return LayerGraphNodeCreation(
            graph = graph.copy(
                nodes = graph.nodes + CompositionNode(
                    id = nodeId,
                    kind = CompositionNodeKind.CAMERA,
                    camera = CameraSettings(),
                ),
            ),
            nodeId = nodeId,
        )
    }

    fun setClipVisible(
        graph: CompositionGraph,
        clipId: String,
        visible: Boolean,
    ): CompositionGraph = updateLayerNode(graph, clipId) { node -> node.copy(visible = visible) }

    fun setClipLocked(
        graph: CompositionGraph,
        clipId: String,
        locked: Boolean,
    ): CompositionGraph = updateLayerNode(graph, clipId) { node -> node.copy(locked = locked) }

    fun setClipParent(
        graph: CompositionGraph,
        clipId: String,
        parentId: String?,
    ): CompositionGraph {
        require(clipId.isNotBlank()) { "clipId must not be blank" }
        val existingLayer = layerNode(graph, clipId)

        if (parentId == null) {
            if (existingLayer == null) return graph
            return replaceNode(graph, existingLayer.copy(parentId = null))
        }

        val parent = graph.nodes.firstOrNull { it.id == parentId }
            ?: throw IllegalArgumentException("parent target does not exist: $parentId")
        require(parent.kind == CompositionNodeKind.NULL || parent.kind == CompositionNodeKind.GROUP) {
            "parent target must be a NULL or GROUP node: $parentId"
        }

        val layer = existingLayer ?: CompositionNode(
            id = nextLayerNodeId(graph, clipId),
            kind = CompositionNodeKind.LAYER,
            clipId = clipId,
        )
        require(!wouldCreateCycle(graph, layer.id, parentId)) {
            "parent assignment would create a composition cycle"
        }

        return if (existingLayer == null) {
            graph.copy(nodes = graph.nodes + layer.copy(parentId = parentId))
        } else {
            replaceNode(graph, layer.copy(parentId = parentId))
        }
    }

    private fun layerNode(graph: CompositionGraph, clipId: String): CompositionNode? =
        graph.nodes.firstOrNull { it.kind == CompositionNodeKind.LAYER && it.clipId == clipId }

    private fun updateLayerNode(
        graph: CompositionGraph,
        clipId: String,
        transform: (CompositionNode) -> CompositionNode,
    ): CompositionGraph {
        require(clipId.isNotBlank()) { "clipId must not be blank" }
        val existingIndex = graph.nodes.indexOfFirst {
            it.kind == CompositionNodeKind.LAYER && it.clipId == clipId
        }
        if (existingIndex >= 0) {
            val nodes = graph.nodes.toMutableList()
            nodes[existingIndex] = transform(nodes[existingIndex])
            return graph.copy(nodes = nodes)
        }

        val nodeId = nextLayerNodeId(graph, clipId)
        return graph.copy(
            nodes = graph.nodes + transform(
                CompositionNode(
                    id = nodeId,
                    kind = CompositionNodeKind.LAYER,
                    clipId = clipId,
                ),
            ),
        )
    }

    private fun replaceNode(graph: CompositionGraph, replacement: CompositionNode): CompositionGraph =
        graph.copy(
            nodes = graph.nodes.map { node ->
                if (node.id == replacement.id) replacement else node
            },
        )

    private fun wouldCreateCycle(graph: CompositionGraph, layerNodeId: String, parentId: String): Boolean {
        val nodes = graph.nodes.associateBy { it.id }
        val visited = mutableSetOf<String>()
        var currentId: String? = parentId
        while (currentId != null) {
            if (currentId == layerNodeId) return true
            if (!visited.add(currentId)) {
                throw IllegalArgumentException("composition graph already contains a parent cycle")
            }
            currentId = nodes[currentId]?.parentId
        }
        return false
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

    private fun nextSequentialNodeId(graph: CompositionGraph, prefix: String): String {
        val occupied = graph.nodes.map { it.id }.toHashSet()
        var suffix = 1L
        while (true) {
            val candidate = "$prefix-$suffix"
            if (candidate !in occupied) return candidate
            check(suffix < Long.MAX_VALUE) { "unable to allocate $prefix node id" }
            suffix++
        }
    }
}
