package com.localcut.editor.domain.design

import com.localcut.editor.domain.model.CaptionMetadata
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignToolsAdvancedTest {
    @Test
    fun mergeAdjacentCaptionsPreservesStyleMetadataAndCombinedTiming() {
        val style = TextStyle(letterSpacingEm = 0.04)
        val caption = CaptionMetadata(languageTag = "pt-BR", speaker = "A")
        val left = TextOverlay("a", 1_000_000L, 750_000L, "olá", style = style, caption = caption)
        val right = TextOverlay("b", 1_750_000L, 1_250_000L, "mundo", style = style, caption = caption)

        val merged = CaptionEditor.mergeAdjacent(left, right)

        assertEquals("a", merged.id)
        assertEquals(1_000_000L, merged.startUs)
        assertEquals(2_000_000L, merged.durationUs)
        assertEquals("olá mundo", merged.text)
        assertEquals(style, merged.style)
        assertEquals(caption, merged.caption)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mergeRejectsNonContiguousCaptions() {
        CaptionEditor.mergeAdjacent(
            TextOverlay("a", 0L, 1_000_000L, "a"),
            TextOverlay("b", 1_100_000L, 1_000_000L, "b"),
        )
    }

    @Test
    fun horizontalDistributionKeepsOuterElementsAndSpacesMiddleEvenly() {
        fun element(id: String, x: Double) = DesignElement(
            id = id,
            startUs = 0L,
            durationUs = 1_000_000L,
            shape = ShapeSpec(ShapeKind.RECTANGLE),
            transform = TransformValue(x = x),
        )
        val result = LayoutEngine.distributeHorizontally(
            listOf(element("left", 0.0), element("middle", 20.0), element("right", 100.0)),
        )

        assertEquals(0.0, result.first { it.id == "left" }.transform.x, 0.0)
        assertEquals(50.0, result.first { it.id == "middle" }.transform.x, 0.0)
        assertEquals(100.0, result.first { it.id == "right" }.transform.x, 0.0)
    }
}
