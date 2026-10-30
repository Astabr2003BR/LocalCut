package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Test

class TextTransformRenderSpecTest {
    @Test
    fun textPositionScaleRotationUseSharedPreviewExportSpec() {
        val overlay = TextOverlay(
            id = "title",
            startUs = 0L,
            durationUs = 2_000_000L,
            text = "LocalCut",
            style = TextStyle(fontSizePx = 64.0),
            transform = TransformValue(
                x = 270.0,
                y = 480.0,
                scale = 1.5,
                rotationDeg = 30.0,
            ),
        )
        val project = Project.create("Text", 1080, 1920, 30).copy(textOverlays = listOf(overlay))

        val spec = TextOverlayRenderSpecCompiler.compile(project).single()

        assertEquals(0.5f, spec.backgroundAnchorX, 0f)
        assertEquals(-0.5f, spec.backgroundAnchorY, 0f)
        assertEquals(0.96f, spec.textScale, 0.0001f)
        assertEquals(30.0f, spec.rotationDegrees, 0f)
    }
}
