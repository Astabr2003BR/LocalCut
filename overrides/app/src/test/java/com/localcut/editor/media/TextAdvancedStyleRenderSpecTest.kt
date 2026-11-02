package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextBackgroundStyle
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextShadowStyle
import com.localcut.editor.domain.model.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class TextAdvancedStyleRenderSpecTest {
    @Test
    fun advancedTextStyleReachesSharedPreviewExportRenderSpec() {
        val overlay = TextOverlay(
            id = "title",
            startUs = 100_000L,
            durationUs = 2_000_000L,
            text = "LocalCut",
            opacity = 0.72,
            style = TextStyle(
                fillArgb = 0xFFFFFFFF.toInt(),
                letterSpacingEm = 0.08,
                background = TextBackgroundStyle(
                    argb = 0xFF1C1C1E.toInt(),
                    opacity = 0.55,
                ),
                shadows = listOf(
                    TextShadowStyle(
                        argb = 0xFF000000.toInt(),
                        radiusPx = 14.0,
                        offsetX = -6.0,
                        offsetY = 10.0,
                        opacity = 0.65,
                    ),
                ),
            ),
        )
        val project = Project.create("Text", 1080, 1920, 30).copy(textOverlays = listOf(overlay))

        val spec = TextOverlayRenderSpecCompiler.compile(project).single()

        assertEquals(0.72f, spec.opacity, 0f)
        assertEquals(0.08, spec.style.letterSpacingEm, 0.0)
        assertEquals(0xFF1C1C1E.toInt(), spec.style.background?.argb)
        assertEquals(0.55, spec.style.background?.opacity ?: -1.0, 0.0)
        assertEquals(14.0, spec.style.shadows.single().radiusPx, 0.0)
        assertEquals(-6.0, spec.style.shadows.single().offsetX, 0.0)
        assertEquals(10.0, spec.style.shadows.single().offsetY, 0.0)
        assertEquals(0.65, spec.style.shadows.single().opacity, 0.0)
    }
}
