package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CameraCut
import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerPanelUiPolicyTest {
    @Test
    fun legacyClipWithoutGraphNodeIsVisibleAndUnlockedByDefault() {
        assertTrue(LayerPanelUiPolicy.isClipVisible(CompositionGraph(), "clip-1"))
        assertFalse(LayerPanelUiPolicy.isClipLocked(CompositionGraph(), "clip-1"))
    }

    @Test
    fun hidingLegacyClipCreatesStableLayerNode() {
        val hidden = LayerPanelUiPolicy.setClipVisible(CompositionGraph(), "clip-1", visible = false)
        val node = hidden.nodes.single()

        assertEquals("layer-clip-1", node.id)
        assertEquals(CompositionNodeKind.LAYER, node.kind)
        assertEquals("clip-1", node.clipId)
        assertFalse(node.visible)
        assertFalse(LayerPanelUiPolicy.isClipVisible(hidden, "clip-1"))
    }

    @Test
    fun lockingLegacyClipCreatesStableLayerNodeAndCanBeReversed() {
        val locked = LayerPanelUiPolicy.setClipLocked(CompositionGraph(), "clip-1", locked = true)
        val node = locked.nodes.single()

        assertEquals("layer-clip-1", node.id)
        assertEquals("clip-1", node.clipId)
        assertTrue(node.locked)
        assertTrue(LayerPanelUiPolicy.isClipLocked(locked, "clip-1"))

        val unlocked = LayerPanelUiPolicy.setClipLocked(locked, "clip-1", locked = false)
        assertFalse(unlocked.nodes.single().locked)
        assertFalse(LayerPanelUiPolicy.isClipLocked(unlocked, "clip-1"))
    }

    @Test
    fun existingLayerNodeLockUpdatePreservesVisibilityParentAndTransform() {
        val source = CompositionGraph(
            nodes = listOf(
                CompositionNode("group", CompositionNodeKind.GROUP),
                CompositionNode(
                    id = "custom-layer",
                    kind = CompositionNodeKind.LAYER,
                    parentId = "group",
                    clipId = "clip-1",
                    visible = false,
                ),
            ),
        )

        val locked = LayerPanelUiPolicy.setClipLocked(source, "clip-1", locked = true)
        val node = locked.nodes.single { it.id == "custom-layer" }

        assertEquals("group", node.parentId)
        assertFalse(node.visible)
        assertTrue(node.locked)
        assertEquals(2, locked.nodes.size)
    }

    @Test
    fun existingLayerNodeIsUpdatedWithoutChangingParentOrTransform() {
        val source = CompositionGraph(
            nodes = listOf(
                CompositionNode("group", CompositionNodeKind.GROUP),
                CompositionNode(
                    id = "custom-layer",
                    kind = CompositionNodeKind.LAYER,
                    parentId = "group",
                    clipId = "clip-1",
                    visible = false,
                ),
            ),
        )

        val visible = LayerPanelUiPolicy.setClipVisible(source, "clip-1", visible = true)
        val node = visible.nodes.single { it.id == "custom-layer" }

        assertEquals("group", node.parentId)
        assertTrue(node.visible)
        assertEquals(2, visible.nodes.size)
    }

    @Test
    fun conflictingGeneratedIdGetsDeterministicSuffix() {
        val source = CompositionGraph(
            nodes = listOf(CompositionNode("layer-clip-1", CompositionNodeKind.NULL)),
        )

        val hidden = LayerPanelUiPolicy.setClipVisible(source, "clip-1", visible = false)

        assertTrue(hidden.nodes.any { it.id == "layer-clip-1-2" && it.clipId == "clip-1" })
    }

    @Test
    fun addNullCreatesUniqueParentTargetWithoutChangingExistingNodes() {
        val source = CompositionGraph(
            nodes = listOf(CompositionNode("null-1", CompositionNodeKind.NULL)),
        )

        val created = LayerPanelUiPolicy.addNull(source)

        assertEquals("null-2", created.nodeId)
        assertEquals(2, created.graph.nodes.size)
        assertTrue(created.graph.nodes.any { it.id == "null-2" && it.kind == CompositionNodeKind.NULL })
    }

    @Test
    fun addStaticCameraCreatesSingleDefaultCameraAndRejectsSecondCamera() {
        val created = LayerPanelUiPolicy.addStaticCamera(CompositionGraph())

        assertEquals("camera-1", created.nodeId)
        assertEquals(CompositionNodeKind.CAMERA, created.graph.nodes.single().kind)
        assertTrue(created.graph.nodes.single().camera != null)

        val second = runCatching { LayerPanelUiPolicy.addStaticCamera(created.graph) }
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull()?.message.orEmpty().contains("camera"))
    }

    @Test
    fun setClipParentCreatesLayerAndLinksItToNullTarget() {
        val source = CompositionGraph(
            nodes = listOf(CompositionNode("null-1", CompositionNodeKind.NULL)),
        )

        val parented = LayerPanelUiPolicy.setClipParent(source, "clip-1", "null-1")
        val layer = parented.nodes.single { it.kind == CompositionNodeKind.LAYER }

        assertEquals("clip-1", layer.clipId)
        assertEquals("null-1", layer.parentId)
        assertEquals("null-1", LayerPanelUiPolicy.layerParentId(parented, "clip-1"))
    }

    @Test
    fun clearClipParentPreservesLayerNodeAndOtherLayerState() {
        val source = CompositionGraph(
            nodes = listOf(
                CompositionNode("null-1", CompositionNodeKind.NULL),
                CompositionNode(
                    id = "custom-layer",
                    kind = CompositionNodeKind.LAYER,
                    parentId = "null-1",
                    clipId = "clip-1",
                    visible = false,
                    locked = false,
                ),
            ),
        )

        val cleared = LayerPanelUiPolicy.setClipParent(source, "clip-1", null)
        val layer = cleared.nodes.single { it.id == "custom-layer" }

        assertNull(layer.parentId)
        assertFalse(layer.visible)
        assertFalse(layer.locked)
    }

    @Test
    fun parentTargetsExposeOnlyNullAndGroupNodes() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("null", CompositionNodeKind.NULL),
                CompositionNode("group", CompositionNodeKind.GROUP),
                CompositionNode("camera", CompositionNodeKind.CAMERA),
                CompositionNode("layer", CompositionNodeKind.LAYER, clipId = "clip-1"),
            ),
        )

        assertEquals(listOf("null", "group"), LayerPanelUiPolicy.parentTargets(graph).map { it.id })
    }

    @Test
    fun parentTargetsForClipExcludeTargetsThatWouldCreateCycle() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("layer", CompositionNodeKind.LAYER, clipId = "clip-1"),
                CompositionNode("unsafe-null", CompositionNodeKind.NULL, parentId = "layer"),
                CompositionNode("safe-group", CompositionNodeKind.GROUP),
            ),
        )

        assertEquals(
            listOf("safe-group"),
            LayerPanelUiPolicy.parentTargets(graph, "clip-1").map { it.id },
        )
    }

    @Test
    fun setClipParentRejectsCameraUnknownTargetAndParentCycle() {
        val source = CompositionGraph(
            nodes = listOf(
                CompositionNode("camera", CompositionNodeKind.CAMERA),
                CompositionNode("layer", CompositionNodeKind.LAYER, clipId = "clip-1", parentId = null),
                CompositionNode("null", CompositionNodeKind.NULL, parentId = "layer"),
            ),
        )

        val camera = runCatching { LayerPanelUiPolicy.setClipParent(source, "clip-1", "camera") }
        assertTrue(camera.isFailure)

        val unknown = runCatching { LayerPanelUiPolicy.setClipParent(source, "clip-1", "missing") }
        assertTrue(unknown.isFailure)

        val cycle = runCatching { LayerPanelUiPolicy.setClipParent(source, "clip-1", "null") }
        assertTrue(cycle.isFailure)
        assertTrue(cycle.exceptionOrNull()?.message.orEmpty().contains("cycle"))
    }

    @Test
    fun planarControllerTransformCanBeEditedButPlaneTiltIsRejected() {
        val graph = CompositionGraph(
            nodes = listOf(CompositionNode("null-1", CompositionNodeKind.NULL)),
        )
        val transform = LayerTransform3D(
            x = 120.0,
            y = -30.0,
            z = 150.0,
            scale = 1.5,
            rotationZDeg = 25.0,
            opacity = 0.75,
        )

        val updated = LayerPanelUiPolicy.setControllerTransform(graph, "null-1", transform)
        assertEquals(transform, updated.nodes.single().transform)

        val tilted = runCatching {
            LayerPanelUiPolicy.setControllerTransform(
                updated,
                "null-1",
                transform.copy(rotationXDeg = 10.0),
            )
        }
        assertTrue(tilted.isFailure)
        assertTrue(tilted.exceptionOrNull()?.message.orEmpty().contains("rotationX/rotationY"))
    }

    @Test
    fun staticCameraSettingsCanBeEditedSafely() {
        val graph = LayerPanelUiPolicy.addStaticCamera(CompositionGraph()).graph
        val settings = CameraSettings(focalLength = 1600.0, orthographic = true)

        val updated = LayerPanelUiPolicy.setCameraSettings(graph, "camera-1", settings)

        assertEquals(settings, updated.nodes.single().camera)
    }

    @Test
    fun removingControllerDetachesChildrenAndCameraCutReferenceIsProtected() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("null-1", CompositionNodeKind.NULL),
                CompositionNode("layer", CompositionNodeKind.LAYER, parentId = "null-1", clipId = "clip-1"),
            ),
        )

        val removed = LayerPanelUiPolicy.removeControllerNode(graph, "null-1")
        assertEquals(1, removed.nodes.size)
        assertNull(removed.nodes.single().parentId)

        val cameraGraph = CompositionGraph(
            nodes = listOf(CompositionNode("camera-1", CompositionNodeKind.CAMERA, camera = CameraSettings())),
            cameraCuts = listOf(CameraCut(0L, "camera-1")),
        )
        val protected = runCatching { LayerPanelUiPolicy.removeControllerNode(cameraGraph, "camera-1") }
        assertTrue(protected.isFailure)
    }

    @Test
    fun removingLayerThroughControllerApiIsRejected() {
        val graph = CompositionGraph(
            nodes = listOf(CompositionNode("layer", CompositionNodeKind.LAYER, clipId = "clip-1")),
        )

        assertTrue(runCatching { LayerPanelUiPolicy.removeControllerNode(graph, "layer") }.isFailure)
    }
}
