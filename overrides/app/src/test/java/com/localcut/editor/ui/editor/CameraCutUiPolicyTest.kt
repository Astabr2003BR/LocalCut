package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CameraCut
import com.localcut.editor.domain.model.CameraSettings
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCutUiPolicyTest {
    @Test
    fun firstCameraRemainsStaticUntilSecondCameraIsCreated() {
        val first = CameraCutUiPolicy.addCamera(CompositionGraph())

        assertEquals(1, first.graph.nodes.count { it.kind == CompositionNodeKind.CAMERA })
        assertTrue(first.graph.cameraCuts.isEmpty())

        val second = CameraCutUiPolicy.addCamera(first.graph)
        val cameras = second.graph.nodes.filter { it.kind == CompositionNodeKind.CAMERA }

        assertEquals(2, cameras.size)
        assertEquals(listOf(CameraCut(0L, first.nodeId)), second.graph.cameraCuts)
        assertEquals(second.nodeId, cameras.last().id)
    }

    @Test
    fun duplicateActiveCameraCopiesSettingsAndCutsToCloneAtPlayhead() {
        val source = CompositionNode(
            id = "camera-a",
            kind = CompositionNodeKind.CAMERA,
            transform = LayerTransform3D(x = 120.0, y = -40.0, z = 50.0, scale = 1.2, rotationZDeg = 8.0),
            camera = CameraSettings(focalLength = 1350.0, orthographic = false),
        )
        val graph = CompositionGraph(nodes = listOf(source))

        val created = CameraCutUiPolicy.duplicateActiveCameraAt(graph, 1_500_000L)
        val clone = created.graph.nodes.single { it.id == created.nodeId }

        assertEquals(source.transform, clone.transform)
        assertEquals(source.camera, clone.camera)
        assertEquals(
            listOf(
                CameraCut(0L, source.id),
                CameraCut(1_500_000L, created.nodeId),
            ),
            created.graph.cameraCuts,
        )
        assertEquals(created.nodeId, CameraCutUiPolicy.activeCameraId(created.graph, 1_500_000L))
    }

    @Test
    fun upsertCutKeepsTimelineOrderedAndReplacesExactTimestamp() {
        val graph = multiCameraGraph()

        val withLateCut = CameraCutUiPolicy.upsertCut(graph, 2_000_000L, "camera-b")
        val withMiddleCut = CameraCutUiPolicy.upsertCut(withLateCut, 1_000_000L, "camera-a")
        val replaced = CameraCutUiPolicy.upsertCut(withMiddleCut, 1_000_000L, "camera-b")

        assertEquals(
            listOf(
                CameraCut(0L, "camera-a"),
                CameraCut(1_000_000L, "camera-b"),
                CameraCut(2_000_000L, "camera-b"),
            ),
            replaced.cameraCuts,
        )
    }

    @Test
    fun activeCameraChangesExactlyAtCutTimestamp() {
        val graph = multiCameraGraph().copy(
            cameraCuts = listOf(
                CameraCut(0L, "camera-a"),
                CameraCut(1_000_000L, "camera-b"),
            ),
        )

        assertEquals("camera-a", CameraCutUiPolicy.activeCameraId(graph, 999_999L))
        assertEquals("camera-b", CameraCutUiPolicy.activeCameraId(graph, 1_000_000L))
    }

    @Test
    fun replacingZeroCutChangesBaseCameraWithoutBreakingTimeline() {
        val graph = multiCameraGraph()

        val updated = CameraCutUiPolicy.upsertCut(graph, 0L, "camera-b")

        assertEquals(CameraCut(0L, "camera-b"), updated.cameraCuts.single())
        assertEquals("camera-b", CameraCutUiPolicy.activeCameraId(updated, 0L))
    }

    @Test
    fun nonBaseCutCanBeRemovedButBaseCutIsProtectedWithMultipleCameras() {
        val graph = multiCameraGraph().copy(
            cameraCuts = listOf(
                CameraCut(0L, "camera-a"),
                CameraCut(1_000_000L, "camera-b"),
            ),
        )

        val removed = CameraCutUiPolicy.removeCut(graph, 1_000_000L)
        assertEquals(listOf(CameraCut(0L, "camera-a")), removed.cameraCuts)

        assertThrows(IllegalArgumentException::class.java) {
            CameraCutUiPolicy.removeCut(graph, 0L)
        }
    }

    @Test
    fun cutRejectsNegativeTimeAndNonCameraTarget() {
        val graph = multiCameraGraph().copy(
            nodes = multiCameraGraph().nodes + CompositionNode(
                id = "null-1",
                kind = CompositionNodeKind.NULL,
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            CameraCutUiPolicy.upsertCut(graph, -1L, "camera-a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CameraCutUiPolicy.upsertCut(graph, 1L, "null-1")
        }
    }

    private fun multiCameraGraph() = CompositionGraph(
        nodes = listOf(
            CompositionNode(
                id = "camera-a",
                kind = CompositionNodeKind.CAMERA,
                camera = CameraSettings(),
            ),
            CompositionNode(
                id = "camera-b",
                kind = CompositionNodeKind.CAMERA,
                camera = CameraSettings(),
            ),
        ),
        cameraCuts = listOf(CameraCut(0L, "camera-a")),
    )
}
