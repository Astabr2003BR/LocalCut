package com.localcut.editor.media

import com.localcut.editor.domain.composition.CameraProjection
import com.localcut.editor.domain.composition.WorldTransformResolver
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class ResolvedCompositionTransform(
    val transform: TransformValue,
    val keyframes: List<TransformKeyframe>,
    val opacity: Double,
)

/**
 * Flattens static LocalCut parenting/null/camera metadata into the legacy 2D transform contract
 * consumed by preview/export. Temporal camera cuts deliberately fail closed until a discrete-cut
 * renderer is available so the editor never silently interpolates between cameras.
 */
internal object CompositionRenderResolver {
    fun resolve(graph: CompositionGraph, clip: Clip): Result<ResolvedCompositionTransform> = runCatching {
        if (graph.nodes.isEmpty()) {
            return@runCatching ResolvedCompositionTransform(
                transform = clip.transform,
                keyframes = clip.keyframes,
                opacity = clip.opacity,
            )
        }

        val layerNodes = graph.nodes.filter { node ->
            node.kind == CompositionNodeKind.LAYER && node.clipId == clip.id
        }
        if (layerNodes.isEmpty()) {
            return@runCatching ResolvedCompositionTransform(
                transform = clip.transform,
                keyframes = clip.keyframes,
                opacity = clip.opacity,
            )
        }
        require(layerNodes.size == 1) { "clip ${clip.id} is referenced by multiple composition layers" }
        val layer = layerNodes.single()
        if (!isEffectivelyVisible(graph, layer.id)) {
            return@runCatching ResolvedCompositionTransform(
                transform = clip.transform,
                keyframes = clip.keyframes,
                opacity = 0.0,
            )
        }

        val cameraNodeId = staticCameraNodeId(graph)
        val world = WorldTransformResolver.resolve(graph, layer.id)
        val container = if (cameraNodeId == null) {
            ContainerTransform(
                x = world.x,
                y = world.y,
                scale = world.scale,
                rotationDeg = world.rotationZDeg,
                opacity = world.opacity,
            )
        } else {
            val projected = CameraProjection.project(graph, layer.id, cameraNodeId)
            val cameraWorld = WorldTransformResolver.resolve(graph, cameraNodeId)
            ContainerTransform(
                x = projected.x,
                y = projected.y,
                scale = projected.scale,
                rotationDeg = world.rotationZDeg - cameraWorld.rotationZDeg,
                opacity = world.opacity,
            )
        }

        ResolvedCompositionTransform(
            transform = compose(container, clip.transform),
            keyframes = clip.keyframes.map { keyframe ->
                keyframe.copy(value = compose(container, keyframe.value))
            },
            opacity = clip.opacity * container.opacity,
        )
    }

    private fun isEffectivelyVisible(graph: CompositionGraph, nodeId: String): Boolean {
        val nodes = graph.nodes.associateBy { it.id }
        val visited = mutableSetOf<String>()
        var current = nodes[nodeId] ?: return true
        while (true) {
            require(visited.add(current.id)) { "composition graph contains a parent cycle" }
            if (!current.visible) return false
            val parentId = current.parentId ?: return true
            current = requireNotNull(nodes[parentId]) { "composition parent does not exist: $parentId" }
        }
    }

    private fun staticCameraNodeId(graph: CompositionGraph): String? {
        if (graph.cameraCuts.size > 1) {
            throw IllegalArgumentException("temporal camera cuts are not supported by the static composition renderer yet")
        }
        graph.cameraCuts.singleOrNull()?.let { cut ->
            require(cut.startUs == 0L) {
                "temporal camera cuts are not supported by the static composition renderer yet"
            }
            return cut.cameraNodeId
        }
        val cameras = graph.nodes.filter { it.kind == CompositionNodeKind.CAMERA }
        return cameras.singleOrNull()?.id
    }

    private fun compose(container: ContainerTransform, local: TransformValue): TransformValue {
        val radians = container.rotationDeg * PI / 180.0
        val scaledX = local.x * container.scale
        val scaledY = local.y * container.scale
        val rotatedX = scaledX * cos(radians) - scaledY * sin(radians)
        val rotatedY = scaledX * sin(radians) + scaledY * cos(radians)
        return TransformValue(
            x = container.x + rotatedX,
            y = container.y + rotatedY,
            scale = container.scale * local.scale,
            rotationDeg = container.rotationDeg + local.rotationDeg,
            opacity = local.opacity,
        )
    }

    private data class ContainerTransform(
        val x: Double,
        val y: Double,
        val scale: Double,
        val rotationDeg: Double,
        val opacity: Double,
    )
}
