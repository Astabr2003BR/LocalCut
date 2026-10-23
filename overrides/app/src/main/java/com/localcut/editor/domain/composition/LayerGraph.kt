package com.localcut.editor.domain.composition

import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object LayerGraph {
    fun validate(graph: CompositionGraph, knownClipIds: Set<String>): List<String> = buildList {
        if (graph.nodes.any { it.id.isBlank() }) add("composition node id must not be blank")
        if (graph.nodes.map { it.id }.distinct().size != graph.nodes.size) add("composition node ids must be unique")
        val nodes = graph.nodes.associateBy { it.id }
        graph.nodes.forEach { node ->
            addAll(node.transform.validationIssues())
            if (node.parentId == node.id) add("composition node cannot parent itself: ${node.id}")
            if (node.parentId != null && nodes[node.parentId] == null) add("composition parent does not exist: ${node.parentId}")
            when (node.kind) {
                CompositionNodeKind.LAYER -> {
                    val clipId = node.clipId
                    if (clipId.isNullOrBlank() || clipId !in knownClipIds) add("layer clip reference is invalid: ${node.id}")
                }
                CompositionNodeKind.CAMERA -> addAll((node.camera ?: CameraSettings()).validationIssues())
                CompositionNodeKind.GROUP, CompositionNodeKind.NULL -> {
                    if (node.clipId != null) add("non-layer node cannot reference a clip: ${node.id}")
                }
            }
        }
        if (hasCycle(graph)) add("composition graph contains a parent cycle")

        if (graph.cameraCuts.any { it.startUs < 0L }) add("camera cuts must use non-negative timestamps")
        if (graph.cameraCuts.zipWithNext().any { (left, right) -> right.startUs <= left.startUs }) {
            add("camera cuts must be strictly ordered")
        }
        graph.cameraCuts.forEach { cut ->
            val cameraNode = nodes[cut.cameraNodeId]
            if (cameraNode == null || cameraNode.kind != CompositionNodeKind.CAMERA) {
                add("camera cut must reference a camera node: ${cut.cameraNodeId}")
            }
        }
    }

    private fun hasCycle(graph: CompositionGraph): Boolean {
        val nodes = graph.nodes.associateBy { it.id }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(id: String): Boolean {
            if (id in visiting) return true
            if (id in visited) return false
            val node = nodes[id] ?: return false
            visiting += id
            val cycle = node.parentId?.let(::visit) == true
            visiting -= id
            visited += id
            return cycle
        }
        return graph.nodes.any { visit(it.id) }
    }
}

object WorldTransformResolver {
    fun resolve(graph: CompositionGraph, nodeId: String): LayerTransform3D {
        val nodes = graph.nodes.associateBy { it.id }
        require(nodes[nodeId] != null) { "composition node not found: $nodeId" }
        val stack = mutableSetOf<String>()

        fun resolveNode(id: String): LayerTransform3D {
            require(stack.add(id)) { "composition graph contains a parent cycle" }
            val node = requireNotNull(nodes[id]) { "composition node not found: $id" }
            val world = node.parentId?.let { parentId ->
                compose(resolveNode(parentId), node.transform)
            } ?: node.transform
            stack.remove(id)
            return world
        }
        return resolveNode(nodeId)
    }

    private fun compose(parent: LayerTransform3D, local: LayerTransform3D): LayerTransform3D {
        val radians = parent.rotationZDeg * PI / 180.0
        val localScaledX = local.x * parent.scale
        val localScaledY = local.y * parent.scale
        val rotatedX = localScaledX * cos(radians) - localScaledY * sin(radians)
        val rotatedY = localScaledX * sin(radians) + localScaledY * cos(radians)
        return LayerTransform3D(
            x = parent.x + rotatedX,
            y = parent.y + rotatedY,
            z = parent.z + local.z,
            scale = parent.scale * local.scale,
            rotationXDeg = parent.rotationXDeg + local.rotationXDeg,
            rotationYDeg = parent.rotationYDeg + local.rotationYDeg,
            rotationZDeg = parent.rotationZDeg + local.rotationZDeg,
            opacity = parent.opacity * local.opacity,
        )
    }
}

data class ProjectedPoint(
    val x: Double,
    val y: Double,
    val scale: Double,
)

object CameraProjection {
    fun project(graph: CompositionGraph, nodeId: String, cameraNodeId: String): ProjectedPoint {
        val nodes = graph.nodes.associateBy { it.id }
        val cameraNode: CompositionNode = requireNotNull(nodes[cameraNodeId]) { "camera node not found" }
        require(cameraNode.kind == CompositionNodeKind.CAMERA) { "node is not a camera" }
        val camera = cameraNode.camera ?: CameraSettings()
        require(camera.validationIssues().isEmpty()) { "invalid camera settings" }
        val world = WorldTransformResolver.resolve(graph, nodeId)
        val cameraWorld = WorldTransformResolver.resolve(graph, cameraNodeId)
        val relativeX = (world.x - cameraWorld.x) / cameraWorld.scale
        val relativeY = (world.y - cameraWorld.y) / cameraWorld.scale
        val relativeZ = world.z - cameraWorld.z
        val perspective = if (camera.orthographic) {
            1.0
        } else {
            val denominator = camera.focalLength - relativeZ
            require(denominator > 1e-6) { "layer is behind the camera projection plane" }
            camera.focalLength / denominator
        }
        return ProjectedPoint(
            x = relativeX * perspective,
            y = relativeY * perspective,
            scale = world.scale * perspective / cameraWorld.scale,
        )
    }
}
