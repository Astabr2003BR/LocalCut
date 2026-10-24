package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAdvancedStyleUiPolicyTest {
    @Test
    fun advancedRendererBackedTextStyleCanBeEditedWithoutInvalidatingOverlay() {
        val source = TextOverlayEditorModel.forEdit(
            TextOverlay(
                id = "title",
                startUs = 0L,
                durationUs = 2_000_000L,
                text = "LocalCut",
            ),
        )

        val edited = source
            .withTextOpacity(0.72)
            .withBackgroundEnabled(true)
            .withBackgroundArgb(0xFF1C1C1E.toInt())
            .withBackgroundOpacity(0.55)
            .withShadowEnabled(true)
            .withShadowArgb(0xFF000000.toInt())
            .withShadowRadiusPx(14.0)
            .withShadowOffset(-6.0, 10.0)
            .withShadowOpacity(0.65)
            .draft

        assertEquals(0.72, edited.opacity, 0.0)
        assertEquals(0xFF1C1C1E.toInt(), edited.style.background?.argb)
        assertEquals(0.55, edited.style.background?.opacity ?: -1.0, 0.0)
        assertEquals(0xFF000000.toInt(), edited.style.shadows.single().argb)
        assertEquals(14.0, edited.style.shadows.single().radiusPx, 0.0)
        assertEquals(-6.0, edited.style.shadows.single().offsetX, 0.0)
        assertEquals(10.0, edited.style.shadows.single().offsetY, 0.0)
        assertEquals(0.65, edited.style.shadows.single().opacity, 0.0)
        assertTrue(edited.style.validationIssues().isEmpty())
    }

    @Test
    fun advancedTextControlsClampToMobileSafeRanges() {
        val source = TextOverlayEditorModel.forEdit(
            TextOverlay("title", 0L, 1_000_000L, "Texto"),
        )
            .withBackgroundEnabled(true)
            .withShadowEnabled(true)

        val edited = source
            .withTextOpacity(5.0)
            .withBackgroundOpacity(-2.0)
            .withShadowRadiusPx(999.0)
            .withShadowOffset(-999.0, 999.0)
            .withShadowOpacity(3.0)
            .draft

        assertEquals(1.0, edited.opacity, 0.0)
        assertEquals(0.0, edited.style.background?.opacity ?: -1.0, 0.0)
        assertEquals(200.0, edited.style.shadows.single().radiusPx, 0.0)
        assertEquals(-200.0, edited.style.shadows.single().offsetX, 0.0)
        assertEquals(200.0, edited.style.shadows.single().offsetY, 0.0)
        assertEquals(1.0, edited.style.shadows.single().opacity, 0.0)
    }

    @Test
    fun advancedSettersAreNoOpsWhenBackgroundOrShadowIsDisabled() {
        val source = TextOverlayEditorModel.forEdit(
            TextOverlay("title", 0L, 1_000_000L, "Texto"),
        )

        val edited = source
            .withBackgroundArgb(0xFFFF0000.toInt())
            .withBackgroundOpacity(0.2)
            .withShadowArgb(0xFFFFFFFF.toInt())
            .withShadowRadiusPx(20.0)
            .withShadowOffset(10.0, 10.0)
            .withShadowOpacity(0.2)
            .draft

        assertEquals(null, edited.style.background)
        assertTrue(edited.style.shadows.isEmpty())
    }
}
