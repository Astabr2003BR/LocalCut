package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.LayerTransform3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerControllerParentUiPolicyTest {
    @Test
    fun nullAndGroupCanBeNestedWithoutOfferingCycles() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("root-group", CompositionNodeKind.GROUP),
                CompositionNode("child-null", CompositionNodeKind.NULL, parentId = "root-group"),
                CompositionNode("leaf-group", CompositionNodeKind.GROUP, parentId = "child-null"),
            ),
        )

        assertEquals("root-group", LayerPanelUiPolicy.controllerParentId(graph, "child-null"))
        assertEquals(
            listOf("root-group"),
            LayerPanelUiPolicy.controllerParentTargets(graph, "child-null").map { it.id },
        )

        val reparented = LayerPanelUiPolicy.setControllerParent(graph, "leaf-group", "root-group")
        assertEquals("root-group", LayerPanelUiPolicy.controllerParentId(reparented, "leaf-group"))

        val cycle = runCatching {
            LayerPanelUiPolicy.setControllerParent(graph, "root-group", "leaf-group")
        }
        assertTrue(cycle.isFailure)
    }

    @Test
    fun cameraCannotBeParentedThroughControllerUi() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("group", CompositionNodeKind.GROUP),
                CompositionNode("camera", CompositionNodeKind.CAMERA),
            ),
        )

        assertTrue(runCatching { LayerPanelUiPolicy.setControllerParent(graph, "camera", "group") }.isFailure)
    }

    @Test
    fun nullAndGroupVisibilityCanBeToggledIndependentlyOfLock() {
        val graph = CompositionGraph(
            nodes = listOf(CompositionNode("group", CompositionNodeKind.GROUP)),
        )

        val hidden = LayerPanelUiPolicy.setControllerVisible(graph, "group", false)
        assertFalse(LayerPanelUiPolicy.isControllerVisible(hidden, "group"))

        val locked = LayerPanelUiPolicy.setControllerLocked(hidden, "group", true)
        assertTrue(LayerPanelUiPolicy.isControllerLocked(locked, "group"))

        val shownWhileLocked = LayerPanelUiPolicy.setControllerVisible(locked, "group", true)
        assertTrue(LayerPanelUiPolicy.isControllerVisible(shownWhileLocked, "group"))
    }

    @Test
    fun lockedControllerRejectsGeometryParentAndRemovalUntilUnlocked() {
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("root", CompositionNodeKind.GROUP),
                CompositionNode("child", CompositionNodeKind.NULL),
            ),
        )
        val locked = LayerPanelUiPolicy.setControllerLocked(graph, "child", true)

        assertTrue(
            runCatching {
                LayerPanelUiPolicy.setControllerTransform(
                    locked,
                    "child",
                    LayerTransform3D(x = 25.0),
                )
            }.isFailure,
        )
        assertTrue(runCatching { LayerPanelUiPolicy.setControllerParent(locked, "child", "root") }.isFailure)
        assertTrue(runCatching { LayerPanelUiPolicy.removeControllerNode(locked, "child") }.isFailure)

        val unlocked = LayerPanelUiPolicy.setControllerLocked(locked, "child", false)
        val moved = LayerPanelUiPolicy.setControllerTransform(
            unlocked,
            "child",
            LayerTransform3D(x = 25.0),
        )
        assertEquals(25.0, moved.nodes.single { it.id == "child" }.transform.x, 0.0)
    }
}
