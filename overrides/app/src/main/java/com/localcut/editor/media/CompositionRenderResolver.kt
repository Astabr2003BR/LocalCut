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

internal data class CompositionTransformContainer(
    val x: Double,
    val y: Double,
    val scale: Double,
    val rotationDeg: Double,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "composition container position must be finite" }
        require(scale.isFinite() && scale > 0.0) { "composition container scale must be finite and positive" }
        require(rotationDeg.isFinite()) { "composition container rotation must be finite" }
    }
}

internal object CompositionTransformMath {
    fun apply(container: CompositionTransformContainer, local: TransformValue): TransformValue {
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

    fun remove(container: CompositionTransformContainer, world: TransformValue): TransformValue {
        val radians = -container.rotationDeg * PI / 180.0
        val translatedX = world.x - container.x
        val translatedY = world.y - container.y
        val unrotatedX = translatedX * cos(radians) - translatedY * sin(radians)
        val unrotatedY = translatedX * sin(radians) + translatedY * cos(radians)
        return TransformValue(
            x = unrotatedX / container.scale,
            y = unrotatedY / container.scale,
            scale = world.scale / container.scale,
            rotationDeg = world.rotationDeg - container.rotationDeg,
            opacity = world.opacity,
        )
    }
}

internal data class ResolvedCompositionTransform(
    val transform: TransformValue,
    val keyframes: List<TransformKeyframe>,
    val opacity: Double,
    val container: CompositionTransformContainer? = null,
)

/**
 * Flattens static LocalCut parenting/null/camera metadata into the legacy 2D transform contract
 * consumed by preview/export. The reversible container is also retained so modern per-property
 * animations can be evaluated in local clip space and then composed into world/camera space.
 * Temporal camera cuts deliberately fail closed until a discrete-cut renderer is available so the
 * editor never silently interpolates between cameras.
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
        requirePlanar(world, "layer ${layer.id}")
        val container = if (cameraNodeId == null) {
            CompositionTransformContainer(
                x = world.x,
                y = world.y,
                scale = world.scale,
                rotationDeg = world.rotationZDeg,
            )
        } else {
            val cameraWorld = WorldTransformResolver.resolve(graph, cameraNodeId)
            requirePlanar(cameraWorld, "camera $cameraNodeId")
            val projected = CameraProjection.project(graph, layer.id, cameraNodeId)
            CompositionTransformContainer(
                x = projected.x,
                y = projected.y,
                scale = projected.scale,
                rotationDeg = world.rotationZDeg - cameraWorld.rotationZDeg,
            )
        }

        ResolvedCompositionTransform(
            transform = CompositionTransformMath.apply(container, clip.transform),
            keyframes = clip.keyframes.map { keyframe ->
                keyframe.copy(value = CompositionTransformMath.apply(container, keyframe.value))
            },
            opacity = clip.opacity * world.opacity,
            container = container,
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

    private fun requirePlanar(transform: LayerTransform3D, subject: String) {
        require(transform.rotationXDeg == 0.0 && transform.rotationYDeg == 0.0) {
            "$subject rotationX/rotationY requires a 3D plane renderer and cannot be flattened safely"
        }
    }
}
