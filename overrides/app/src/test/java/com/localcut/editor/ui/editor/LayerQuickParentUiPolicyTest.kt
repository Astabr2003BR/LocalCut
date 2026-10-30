package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerQuickParentUiPolicyTest {
    @Test
    fun addNullParentCreatesControllerAndParentsClipAtomically() {
        val created = LayerPanelUiPolicy.addNullParentForClip(CompositionGraph(), "clip-1")

        assertEquals("null-1", created.nodeId)
        assertEquals("null-1", LayerPanelUiPolicy.layerParentId(created.graph, "clip-1"))
        assertTrue(created.graph.nodes.any { it.id == "null-1" && it.kind == CompositionNodeKind.NULL })
    }

    @Test
    fun addGroupParentCreatesControllerAndParentsClipAtomically() {
        val created = LayerPanelUiPolicy.addGroupParentForClip(CompositionGraph(), "clip-1")

        assertEquals("group-1", created.nodeId)
        assertEquals("group-1", LayerPanelUiPolicy.layerParentId(created.graph, "clip-1"))
        assertTrue(created.graph.nodes.any { it.id == "group-1" && it.kind == CompositionNodeKind.GROUP })
    }

    @Test
    fun lockedLayerRejectsQuickParentAndReparentUntilUnlocked() {
        val locked = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "layer-clip-1",
                    kind = CompositionNodeKind.LAYER,
                    clipId = "clip-1",
                    locked = true,
                ),
                CompositionNode(
                    id = "group-1",
                    kind = CompositionNodeKind.GROUP,
                ),
            ),
        )

        assertTrue(runCatching { LayerPanelUiPolicy.addNullParentForClip(locked, "clip-1") }.isFailure)
        assertTrue(runCatching { LayerPanelUiPolicy.addGroupParentForClip(locked, "clip-1") }.isFailure)
        assertTrue(runCatching { LayerPanelUiPolicy.setClipParent(locked, "clip-1", "group-1") }.isFailure)

        val unlocked = LayerPanelUiPolicy.setClipLocked(locked, "clip-1", false)
        val reparented = LayerPanelUiPolicy.setClipParent(unlocked, "clip-1", "group-1")
        assertEquals("group-1", LayerPanelUiPolicy.layerParentId(reparented, "clip-1"))
    }
}
