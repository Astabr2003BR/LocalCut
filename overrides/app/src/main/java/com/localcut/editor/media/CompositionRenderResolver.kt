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

data class CompositionTransformContainer(
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

data class TimedCompositionTransformContainer(
    val startUs: Long,
    val container: CompositionTransformContainer,
)

data class CompositionTransformTimeline(
    val entries: List<TimedCompositionTransformContainer>,
) {
    init {
        require(entries.isNotEmpty()) { "composition transform timeline must not be empty" }
        require(entries.first().startUs == 0L) { "composition transform timeline must start at zero" }
        require(entries.all { it.startUs >= 0L }) { "composition transform timeline timestamps must be non-negative" }
        require(entries.zipWithNext().all { (left, right) -> right.startUs > left.startUs }) {
            "composition transform timeline must be strictly ordered"
        }
    }

    fun containerAt(presentationTimeUs: Long): CompositionTransformContainer {
        require(presentationTimeUs >= 0L) { "presentation time must be non-negative" }
        return entries.last { it.startUs <= presentationTimeUs }.container
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
    val containerTimeline: CompositionTransformTimeline? = null,
)

/**
 * Flattens LocalCut parenting/null/camera metadata into the legacy 2D transform contract consumed
 * by preview/export. A reversible baseline container keeps modern PropertyAnimation in local clip
 * space. When camera cuts exist, a strictly discrete container timeline selects the active camera
 * at each presentation timestamp without physically splitting the clip or interpolating cameras.
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

        val world = WorldTransformResolver.resolve(graph, layer.id)
        requirePlanar(world, "layer ${layer.id}")
        val timeline = cameraContainerTimeline(graph, layer.id, world)
        val baselineContainer = timeline?.containerAt(clip.startUs) ?: containerFor(
            graph = graph,
            layerId = layer.id,
            world = world,
            cameraNodeId = staticCameraNodeId(graph),
        )

        ResolvedCompositionTransform(
            transform = CompositionTransformMath.apply(baselineContainer, clip.transform),
            keyframes = clip.keyframes.map { keyframe ->
                keyframe.copy(value = CompositionTransformMath.apply(baselineContainer, keyframe.value))
            },
            opacity = clip.opacity * world.opacity,
            container = baselineContainer,
            containerTimeline = timeline,
        )
    }

    private fun cameraContainerTimeline(
        graph: CompositionGraph,
        layerId: String,
        world: LayerTransform3D,
    ): CompositionTransformTimeline? {
        if (graph.cameraCuts.isEmpty()) return null
        require(graph.cameraCuts.all { it.startUs >= 0L }) { "camera cuts must use non-negative timestamps" }
        require(graph.cameraCuts.zipWithNext().all { (left, right) -> right.startUs > left.startUs }) {
            "camera cuts must be strictly ordered"
        }

        val cutAtZero = graph.cameraCuts.firstOrNull()?.takeIf { it.startUs == 0L }
        val entries = buildList {
            add(
                TimedCompositionTransformContainer(
                    startUs = 0L,
                    container = containerFor(
                        graph = graph,
                        layerId = layerId,
                        world = world,
                        cameraNodeId = cutAtZero?.cameraNodeId,
                    ),
                )
            )
            graph.cameraCuts.forEach { cut ->
                if (cut.startUs == 0L) return@forEach
                add(
                    TimedCompositionTransformContainer(
                        startUs = cut.startUs,
                        container = containerFor(
                            graph = graph,
                            layerId = layerId,
                            world = world,
                            cameraNodeId = cut.cameraNodeId,
                        ),
                    )
                )
            }
        }
        return CompositionTransformTimeline(entries)
    }

    private fun containerFor(
        graph: CompositionGraph,
        layerId: String,
        world: LayerTransform3D,
        cameraNodeId: String?,
    ): CompositionTransformContainer {
        if (cameraNodeId == null) {
            return CompositionTransformContainer(
                x = world.x,
                y = world.y,
                scale = world.scale,
                rotationDeg = world.rotationZDeg,
            )
        }

        val cameraNode = graph.nodes.firstOrNull { it.id == cameraNodeId }
        require(cameraNode?.kind == CompositionNodeKind.CAMERA) {
            "camera cut must reference a camera node: $cameraNodeId"
        }
        val cameraWorld = WorldTransformResolver.resolve(graph, cameraNodeId)
        requirePlanar(cameraWorld, "camera $cameraNodeId")
        val projected = CameraProjection.project(graph, layerId, cameraNodeId)
        return CompositionTransformContainer(
            x = projected.x,
            y = projected.y,
            scale = projected.scale,
            rotationDeg = world.rotationZDeg - cameraWorld.rotationZDeg,
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
        if (graph.cameraCuts.isNotEmpty()) return null
        val cameras = graph.nodes.filter { it.kind == CompositionNodeKind.CAMERA }
        return cameras.singleOrNull()?.id
    }

    private fun requirePlanar(transform: LayerTransform3D, subject: String) {
        require(transform.rotationXDeg == 0.0 && transform.rotationYDeg == 0.0) {
            "$subject rotationX/rotationY requires a 3D plane renderer and cannot be flattened safely"
        }
    }
}
