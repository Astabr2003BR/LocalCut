package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph
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
}
