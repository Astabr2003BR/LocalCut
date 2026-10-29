package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import org.junit.Assert.assertEquals
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
}
