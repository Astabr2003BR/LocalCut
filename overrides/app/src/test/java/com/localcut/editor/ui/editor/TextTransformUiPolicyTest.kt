package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Test

class TextTransformUiPolicyTest {
    @Test
    fun textTransformControlsUseCanvasBoundsAndSafeScaleRotationRanges() {
        val project = Project.create("Text", 1080, 1920, 30)
        val model = TextOverlayEditorModel.forEdit(
            TextOverlay("title", 0L, 1_000_000L, "Texto"),
        )

        val edited = model
            .withPosition(project, 9_999.0, -9_999.0)
            .withScale(8.0)
            .withRotationDeg(500.0)
            .draft

        assertEquals(540.0, edited.transform.x, 0.0)
        assertEquals(-960.0, edited.transform.y, 0.0)
        assertEquals(4.0, edited.transform.scale, 0.0)
        assertEquals(180.0, edited.transform.rotationDeg, 0.0)
    }

    @Test
    fun textTransformControlsPreserveUnrelatedTextState() {
        val project = Project.create("Text", 1080, 1920, 30)
        val original = TextOverlay(
            id = "title",
            startUs = 200_000L,
            durationUs = 2_000_000L,
            text = "LocalCut",
            opacity = 0.8,
            zIndex = 7,
        )

        val edited = TextOverlayEditorModel.forEdit(original)
            .withPosition(project, 120.0, -240.0)
            .withScale(1.5)
            .withRotationDeg(30.0)
            .draft

        assertEquals("LocalCut", edited.text)
        assertEquals(0.8, edited.opacity, 0.0)
        assertEquals(7, edited.zIndex)
        assertEquals(120.0, edited.transform.x, 0.0)
        assertEquals(-240.0, edited.transform.y, 0.0)
        assertEquals(1.5, edited.transform.scale, 0.0)
        assertEquals(30.0, edited.transform.rotationDeg, 0.0)
    }
}
