package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayValidationTest {
    @Test
    fun validTextOverlayIsAccepted() {
        val project = baseProject().copy(
            textOverlays = listOf(TextOverlay("title", 0L, 1_000_000L, "LocalCut")),
        )

        assertFalse(hasInvalidOverlay(project))
    }

    @Test
    fun textOverlayRejectsInvalidTimingAndBlankText() {
        val negativeStart = baseProject().copy(
            textOverlays = listOf(TextOverlay("negative", -1L, 1_000_000L, "Text")),
        )
        val zeroDuration = baseProject().copy(
            textOverlays = listOf(TextOverlay("zero", 0L, 0L, "Text")),
        )
        val blankText = baseProject().copy(
            textOverlays = listOf(TextOverlay("blank", 0L, 1_000_000L, "   ")),
        )

        assertTrue(hasInvalidOverlay(negativeStart))
        assertTrue(hasInvalidOverlay(zeroDuration))
        assertTrue(hasInvalidOverlay(blankText))
    }

    @Test
    fun textOverlayRejectsInvalidStyleTransformOrOpacity() {
        val invalidStyle = baseProject().copy(
            textOverlays = listOf(
                TextOverlay("style", 0L, 1_000_000L, "Text", style = TextStyle(fontSizePx = 0.0)),
            ),
        )
        val invalidTransform = baseProject().copy(
            textOverlays = listOf(
                TextOverlay("transform", 0L, 1_000_000L, "Text", transform = TransformValue(scale = 0.0)),
            ),
        )
        val invalidOpacity = baseProject().copy(
            textOverlays = listOf(TextOverlay("opacity", 0L, 1_000_000L, "Text", opacity = 1.1)),
        )

        assertTrue(hasInvalidOverlay(invalidStyle))
        assertTrue(hasInvalidOverlay(invalidTransform))
        assertTrue(hasInvalidOverlay(invalidOpacity))
    }

    @Test
    fun duplicateTextOverlayIdsAreRejected() {
        val project = baseProject().copy(
            textOverlays = listOf(
                TextOverlay("same", 0L, 1_000_000L, "A"),
                TextOverlay("same", 1_000_000L, 1_000_000L, "B"),
            ),
        )

        assertTrue(
            TimelineValidator.validate(project).any {
                it.code == ProjectIssueCode.DUPLICATE_TEXT_OVERLAY_ID && it.subjectId == "same"
            },
        )
    }

    private fun hasInvalidOverlay(project: Project): Boolean =
        TimelineValidator.validate(project).any { it.code == ProjectIssueCode.INVALID_TEXT_OVERLAY }

    private fun baseProject(): Project = Project.create("Text", 1080, 1920, 30)
}
