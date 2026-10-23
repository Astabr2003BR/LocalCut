package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerPanelUiPolicyTest {
    @Test
    fun legacyClipWithoutGraphNodeIsVisibleByDefault() {
        assertTrue(LayerPanelUiPolicy.isClipVisible(CompositionGraph(), "clip-1"))
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
}
