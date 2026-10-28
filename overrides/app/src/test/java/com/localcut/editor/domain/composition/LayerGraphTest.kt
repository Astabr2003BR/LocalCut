package com.localcut.editor.domain.composition

import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import com.localcut.editor.domain.model.CameraCut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerGraphTest {
    @Test
    fun parentTransformIsInheritedByChild() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "parent",
                    kind = CompositionNodeKind.NULL,
                    transform = LayerTransform3D(x = 10.0, y = 20.0, z = 5.0, scale = 2.0, opacity = 0.5),
                ),
                CompositionNode(
                    id = "child",
                    kind = CompositionNodeKind.LAYER,
                    parentId = "parent",
                    clipId = "clip-1",
                    transform = LayerTransform3D(x = 5.0, y = 10.0, z = 2.0, scale = 0.5, opacity = 0.8),
                ),
            ),
        )

        val world = WorldTransformResolver.resolve(graph, "child")

        assertEquals(20.0, world.x, 1e-9)
        assertEquals(40.0, world.y, 1e-9)
        assertEquals(7.0, world.z, 1e-9)
        assertEquals(1.0, world.scale, 1e-9)
        assertEquals(0.4, world.opacity, 1e-9)
    }

    @Test
    fun cycleIsRejected() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("a", CompositionNodeKind.NULL, parentId = "b"),
                CompositionNode("b", CompositionNodeKind.NULL, parentId = "a"),
            ),
        )

        val issues = LayerGraph.validate(graph, emptySet())

        assertTrue(issues.any { "cycle" in it })
    }

    @Test
    fun layerReferenceMustPointToKnownClip() {
        val graph = CompositionGraph(
            nodes = listOf(CompositionNode("layer", CompositionNodeKind.LAYER, clipId = "missing")),
        )

        assertTrue(LayerGraph.validate(graph, setOf("known")).any { "clip" in it })
    }

    @Test
    fun duplicateLayerReferencesForSameClipAreRejected() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("layer-a", CompositionNodeKind.LAYER, clipId = "clip-1"),
                CompositionNode("layer-b", CompositionNodeKind.LAYER, clipId = "clip-1"),
            ),
        )

        val issues = LayerGraph.validate(graph, setOf("clip-1"))

        assertTrue(issues.any { "unique" in it && "clip-1" in it })
    }

    @Test
    fun cameraCutsMustBeOrderedAndReferenceCameraNodes() {
        val graph = CompositionGraph(
            nodes = listOf(CompositionNode("camera", CompositionNodeKind.CAMERA)),
            cameraCuts = listOf(
                CameraCut(1_000_000L, "camera"),
                CameraCut(500_000L, "camera"),
            ),
        )

        assertTrue(LayerGraph.validate(graph, emptySet()).any { "camera cuts" in it })
    }

    @Test
    fun cameraProjectionCreatesDepthParallax() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "camera",
                    kind = CompositionNodeKind.CAMERA,
                    transform = LayerTransform3D(z = 0.0),
                    camera = CameraSettings(focalLength = 1000.0),
                ),
                CompositionNode(
                    id = "near",
                    kind = CompositionNodeKind.LAYER,
                    clipId = "near-clip",
                    transform = LayerTransform3D(x = 100.0, z = 100.0),
                ),
                CompositionNode(
                    id = "far",
                    kind = CompositionNodeKind.LAYER,
                    clipId = "far-clip",
                    transform = LayerTransform3D(x = 100.0, z = -100.0),
                ),
            ),
        )

        val near = CameraProjection.project(graph, "near", "camera")
        val far = CameraProjection.project(graph, "far", "camera")

        assertTrue(near.x > far.x)
    }

    @Test
    fun cameraRotationUsesInverseViewRotationForProjectedPosition() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "camera",
                    kind = CompositionNodeKind.CAMERA,
                    transform = LayerTransform3D(rotationZDeg = 90.0),
                    camera = CameraSettings(orthographic = true),
                ),
                CompositionNode(
                    id = "layer",
                    kind = CompositionNodeKind.LAYER,
                    clipId = "clip-1",
                    transform = LayerTransform3D(x = 100.0),
                ),
            ),
        )

        val projected = CameraProjection.project(graph, "layer", "camera")

        assertEquals(0.0, projected.x, 1e-9)
        assertEquals(-100.0, projected.y, 1e-9)
    }
}
