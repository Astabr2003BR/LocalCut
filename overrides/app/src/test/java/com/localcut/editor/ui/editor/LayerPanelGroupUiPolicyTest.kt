package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerPanelGroupUiPolicyTest {
    @Test
    fun addGroupCreatesUniqueEditableParentTarget() {
        val source = CompositionGraph(
            nodes = listOf(
                CompositionNode("group-1", CompositionNodeKind.GROUP),
                CompositionNode("null-1", CompositionNodeKind.NULL),
            ),
        )

        val created = LayerPanelUiPolicy.addGroup(source)

        assertEquals("group-2", created.nodeId)
        assertEquals(3, created.graph.nodes.size)
        assertTrue(
            created.graph.nodes.any {
                it.id == "group-2" && it.kind == CompositionNodeKind.GROUP
            },
        )
        assertTrue(LayerPanelUiPolicy.parentTargets(created.graph).any { it.id == "group-2" })
    }
}
