package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraRigUiPolicyTest {
    @Test
    fun cameraPresetsAreValidDistinctAndIncludeOrthographic() {
        val presets = CameraPresetUiPolicy.presets()

        assertTrue(presets.size >= 4)
        assertEquals(presets.map { it.id }.distinct().size, presets.size)
        assertTrue(presets.any { it.settings.orthographic })
        assertTrue(presets.any { !it.settings.orthographic && it.settings.focalLength < CameraSettings().focalLength })
        assertTrue(presets.any { !it.settings.orthographic && it.settings.focalLength > CameraSettings().focalLength })
        assertTrue(presets.all { it.settings.validationIssues().isEmpty() })
    }

    @Test
    fun applyingPresetPreservesCameraIdentityAndTransform() {
        val camera = CompositionNode(
            id = "camera-1",
            kind = CompositionNodeKind.CAMERA,
            camera = CameraSettings(),
        )
        val graph = CompositionGraph(nodes = listOf(camera))
        val preset = CameraPresetUiPolicy.presets().first { it.id == "perspective-strong" }

        val updated = CameraPresetUiPolicy.apply(graph, camera.id, preset.id)
        val result = updated.nodes.single()

        assertEquals(camera.id, result.id)
        assertEquals(camera.transform, result.transform)
        assertEquals(preset.settings, result.camera)
    }

    @Test
    fun cameraCanBeParentedToNullOrGroupAndDetached() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("null-1", CompositionNodeKind.NULL),
                CompositionNode("group-1", CompositionNodeKind.GROUP),
                CompositionNode("camera-1", CompositionNodeKind.CAMERA, camera = CameraSettings()),
            ),
        )

        assertEquals(
            listOf("null-1", "group-1"),
            CameraRigUiPolicy.parentTargets(graph, "camera-1").map { it.id },
        )

        val parented = CameraRigUiPolicy.setParent(graph, "camera-1", "null-1")
        assertEquals("null-1", CameraRigUiPolicy.parentId(parented, "camera-1"))

        val detached = CameraRigUiPolicy.setParent(parented, "camera-1", null)
        assertNull(CameraRigUiPolicy.parentId(detached, "camera-1"))
    }

    @Test
    fun cameraParentingRejectsLockedCameraInvalidTargetAndCycle() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("camera-1", CompositionNodeKind.CAMERA, camera = CameraSettings()),
                CompositionNode("layer-1", CompositionNodeKind.LAYER, clipId = "clip-1"),
                CompositionNode("null-1", CompositionNodeKind.NULL, parentId = "camera-1"),
            ),
        )

        assertTrue(runCatching {
            CameraRigUiPolicy.setParent(graph, "camera-1", "layer-1")
        }.isFailure)
        assertTrue(runCatching {
            CameraRigUiPolicy.setParent(graph, "camera-1", "null-1")
        }.isFailure)

        val locked = graph.copy(
            nodes = graph.nodes.map { if (it.id == "camera-1") it.copy(locked = true) else it },
        )
        assertTrue(runCatching {
            CameraRigUiPolicy.setParent(locked, "camera-1", null)
        }.isFailure)
        assertFalse(LayerPanelUiPolicy.isControllerLocked(graph, "camera-1"))
    }
}
