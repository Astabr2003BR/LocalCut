package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind

object CameraRigUiPolicy {
    fun parentId(graph: CompositionGraph, cameraNodeId: String): String? =
        cameraNode(graph, cameraNodeId).parentId

    fun parentTargets(graph: CompositionGraph, cameraNodeId: String): List<CompositionNode> {
        cameraNode(graph, cameraNodeId)
        return graph.nodes.filter { candidate ->
            candidate.id != cameraNodeId &&
                (candidate.kind == CompositionNodeKind.NULL || candidate.kind == CompositionNodeKind.GROUP) &&
                runCatching { !wouldCreateCycle(graph, cameraNodeId, candidate.id) }.getOrDefault(false)
        }
    }

    fun setParent(
        graph: CompositionGraph,
        cameraNodeId: String,
        parentId: String?,
    ): CompositionGraph {
        val camera = cameraNode(graph, cameraNodeId)
        require(!camera.locked) { "controller is locked: $cameraNodeId" }
        if (parentId == null) return replaceNode(graph, camera.copy(parentId = null))

        val parent = graph.nodes.firstOrNull { it.id == parentId }
            ?: throw IllegalArgumentException("parent target does not exist: $parentId")
        require(parent.kind == CompositionNodeKind.NULL || parent.kind == CompositionNodeKind.GROUP) {
            "camera parent target must be a NULL or GROUP node: $parentId"
        }
        require(!wouldCreateCycle(graph, cameraNodeId, parentId)) {
            "parent assignment would create a composition cycle"
        }
        return replaceNode(graph, camera.copy(parentId = parentId))
    }

    private fun cameraNode(graph: CompositionGraph, cameraNodeId: String): CompositionNode {
        require(cameraNodeId.isNotBlank()) { "cameraNodeId must not be blank" }
        val node = graph.nodes.firstOrNull { it.id == cameraNodeId }
            ?: throw IllegalArgumentException("composition node does not exist: $cameraNodeId")
        require(node.kind == CompositionNodeKind.CAMERA) { "node is not a camera: $cameraNodeId" }
        return node
    }

    private fun replaceNode(graph: CompositionGraph, replacement: CompositionNode): CompositionGraph =
        graph.copy(
            nodes = graph.nodes.map { node ->
                if (node.id == replacement.id) replacement else node
            },
        )

    private fun wouldCreateCycle(graph: CompositionGraph, nodeId: String, parentId: String): Boolean {
        val nodes = graph.nodes.associateBy { it.id }
        val visited = mutableSetOf<String>()
        var currentId: String? = parentId
        while (currentId != null) {
            if (currentId == nodeId) return true
            if (!visited.add(currentId)) {
                throw IllegalArgumentException("composition graph already contains a parent cycle")
            }
            currentId = nodes[currentId]?.parentId
        }
        return false
    }
}
