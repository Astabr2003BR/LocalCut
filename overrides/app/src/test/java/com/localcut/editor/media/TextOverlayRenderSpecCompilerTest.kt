package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayRenderSpecCompilerTest {
    @Test
    fun specsAreSortedBottomToTopByZIndex() {
        val project = baseProject().copy(
            textOverlays = listOf(
                TextOverlay("top", 0L, 2_000_000L, "Top", zIndex = 20),
                TextOverlay("bottom", 0L, 2_000_000L, "Bottom", zIndex = 2),
            ),
        )

        val specs = TextOverlayRenderSpecCompiler.compile(project)

        assertEquals(listOf("bottom", "top"), specs.map { it.id })
    }

    @Test
    fun activeWindowIsHalfOpenAndPreservesOpacity() {
        val overlay = TextOverlay("title", 1_000_000L, 2_000_000L, "Title", opacity = 0.6)
        val spec = TextOverlayRenderSpecCompiler.compile(baseProject().copy(textOverlays = listOf(overlay))).single()

        assertFalse(spec.isActiveAt(999_999L))
        assertTrue(spec.isActiveAt(1_000_000L))
        assertTrue(spec.isActiveAt(2_999_999L))
        assertFalse(spec.isActiveAt(3_000_000L))
        assertEquals(0.6f, spec.alphaAt(2_000_000L), 0.0001f)
        assertEquals(0f, spec.alphaAt(3_000_000L), 0.0001f)
    }

    @Test
    fun projectPixelTransformMapsToNormalizedMedia3Coordinates() {
        val overlay = TextOverlay(
            id = "positioned",
            startUs = 0L,
            durationUs = 1_000_000L,
            text = "Text",
            style = TextStyle(fontSizePx = 50.0),
            transform = TransformValue(x = 270.0, y = 480.0, scale = 2.0, rotationDeg = 15.0),
        )
        val spec = TextOverlayRenderSpecCompiler.compile(baseProject().copy(textOverlays = listOf(overlay))).single()

        assertEquals(0.5f, spec.backgroundAnchorX, 0.0001f)
        assertEquals(-0.5f, spec.backgroundAnchorY, 0.0001f)
        assertEquals(1.0f, spec.textScale, 0.0001f)
        assertEquals(15.0f, spec.rotationDegrees, 0.0001f)
    }

    private fun baseProject(): Project = Project.create("Text render", 1080, 1920, 30)
}
