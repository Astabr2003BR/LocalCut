package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CameraCut
import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind

object CameraCutUiPolicy {
    fun addCamera(graph: CompositionGraph): LayerGraphNodeCreation {
        val cameras = graph.nodes.filter { it.kind == CompositionNodeKind.CAMERA }
        require(graph.cameraCuts.isNotEmpty() || cameras.size <= 1) {
            "multiple cameras without camera cuts are ambiguous"
        }

        val nodeId = nextCameraId(graph)
        val created = CompositionNode(
            id = nodeId,
            kind = CompositionNodeKind.CAMERA,
            camera = CameraSettings(),
        )
        val cuts = when {
            cameras.isEmpty() -> graph.cameraCuts
            cameras.size == 1 && graph.cameraCuts.isEmpty() -> listOf(CameraCut(0L, cameras.single().id))
            else -> graph.cameraCuts
        }
        return LayerGraphNodeCreation(
            graph = graph.copy(
                nodes = graph.nodes + created,
                cameraCuts = cuts,
            ),
            nodeId = nodeId,
        )
    }

    fun activeCameraId(graph: CompositionGraph, playheadUs: Long): String? {
        require(playheadUs >= 0L) { "playhead must be non-negative" }
        val cuts = graph.cameraCuts
        if (cuts.isNotEmpty()) {
            return cuts.lastOrNull { it.startUs <= playheadUs }?.cameraNodeId
        }
        return graph.nodes.filter { it.kind == CompositionNodeKind.CAMERA }.singleOrNull()?.id
    }

    fun upsertCut(
        graph: CompositionGraph,
        startUs: Long,
        cameraNodeId: String,
    ): CompositionGraph {
        require(startUs >= 0L) { "camera cut time must be non-negative" }
        val camera = graph.nodes.firstOrNull { it.id == cameraNodeId }
            ?: throw IllegalArgumentException("camera node does not exist: $cameraNodeId")
        require(camera.kind == CompositionNodeKind.CAMERA) {
            "camera cut target must be a camera node: $cameraNodeId"
        }

        val cameras = graph.nodes.count { it.kind == CompositionNodeKind.CAMERA }
        require(cameras >= 1) { "camera cut requires at least one camera" }

        val seededCuts = when {
            graph.cameraCuts.isNotEmpty() -> graph.cameraCuts
            cameras == 1 -> emptyList()
            else -> throw IllegalArgumentException("multiple cameras require a base camera cut at zero")
        }
        val updated = (seededCuts.filterNot { it.startUs == startUs } + CameraCut(startUs, cameraNodeId))
            .sortedBy { it.startUs }
        require(updated.zipWithNext().all { (left, right) -> right.startUs > left.startUs }) {
            "camera cuts must be strictly ordered"
        }
        return graph.copy(cameraCuts = updated)
    }

    fun removeCut(graph: CompositionGraph, startUs: Long): CompositionGraph {
        require(startUs >= 0L) { "camera cut time must be non-negative" }
        val existing = graph.cameraCuts.firstOrNull { it.startUs == startUs }
            ?: return graph
        val cameraCount = graph.nodes.count { it.kind == CompositionNodeKind.CAMERA }
        require(!(existing.startUs == 0L && cameraCount > 1)) {
            "base camera cut cannot be removed while multiple cameras exist"
        }
        return graph.copy(cameraCuts = graph.cameraCuts.filterNot { it.startUs == startUs })
    }

    fun exactCutAt(graph: CompositionGraph, playheadUs: Long): CameraCut? {
        require(playheadUs >= 0L) { "playhead must be non-negative" }
        return graph.cameraCuts.firstOrNull { it.startUs == playheadUs }
    }

    private fun nextCameraId(graph: CompositionGraph): String {
        val occupied = graph.nodes.mapTo(hashSetOf()) { it.id }
        var suffix = 1L
        while (true) {
            val candidate = "camera-$suffix"
            if (candidate !in occupied) return candidate
            check(suffix < Long.MAX_VALUE) { "unable to allocate camera node id" }
            suffix++
        }
    }
}
