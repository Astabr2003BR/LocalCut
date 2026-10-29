package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraDuplicateRigRegressionTest {
    @Test
    fun duplicateActiveCameraPreservesValidRigParentAndLocalTransform() {
        val parent = CompositionNode(
            id = "null-rig",
            kind = CompositionNodeKind.NULL,
            transform = LayerTransform3D(x = 80.0, y = -20.0, rotationZDeg = 5.0),
        )
        val camera = CompositionNode(
            id = "camera-a",
            kind = CompositionNodeKind.CAMERA,
            parentId = parent.id,
            transform = LayerTransform3D(x = 120.0, y = 40.0, z = 30.0, scale = 1.1),
            camera = CameraSettings(focalLength = 1200.0),
        )
        val graph = CompositionGraph(nodes = listOf(parent, camera))

        val duplicated = CameraCutUiPolicy.duplicateActiveCameraAt(graph, 1_000_000L)
        val clone = duplicated.graph.nodes.single { it.id == duplicated.nodeId }

        assertEquals(camera.parentId, clone.parentId)
        assertEquals(camera.transform, clone.transform)
        assertEquals(camera.camera, clone.camera)
    }
}
