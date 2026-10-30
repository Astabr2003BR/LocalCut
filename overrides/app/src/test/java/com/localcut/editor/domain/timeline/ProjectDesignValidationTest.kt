package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDesignValidationTest {
    @Test
    fun missingDesignReferencesAndBrokenReusableComponentsAreRejected() {
        val project = Project.create("Design", 1080, 1920, 30).copy(
            designElements = listOf(
                DesignElement(
                    id = "element-1",
                    startUs = 0L,
                    durationUs = 1_000_000L,
                    clipId = "missing-clip",
                ),
            ),
            designCanvas = DesignCanvasSettings(
                guides = listOf(
                    CanvasGuide("guide", GuideAxis.VERTICAL, 0.25),
                    CanvasGuide("guide", GuideAxis.HORIZONTAL, 0.75),
                ),
            ),
            reusableComponents = listOf(
                ReusableComponent(
                    id = "component-1",
                    name = "Broken",
                    elementIds = listOf("missing-element"),
                ),
            ),
        )

        val issues = TimelineValidator.validate(project)

        assertTrue(issues.any { it.code == ProjectIssueCode.INVALID_DESIGN_STATE && "clip" in (it.detail ?: "") })
        assertTrue(issues.any { it.code == ProjectIssueCode.INVALID_DESIGN_STATE && "guide" in (it.detail ?: "") })
        assertTrue(issues.any { it.code == ProjectIssueCode.INVALID_DESIGN_STATE && "missing" in (it.detail ?: "") })
    }

    @Test
    fun advancedTextValidationRejectsInvalidAnimationCaptionAndSpacing() {
        val overlay = TextOverlay(
            id = "caption-1",
            startUs = 0L,
            durationUs = 1_000_000L,
            text = "caption",
            style = TextStyle(lineSpacingMultiplier = 0.0),
            entrance = TextAnimationDescriptor(durationUs = -1L),
            perCharacterDelayUs = -1L,
            caption = CaptionMetadata(confidence = 2.0),
        )
        val project = Project.create("Text", 1080, 1920, 30).copy(textOverlays = listOf(overlay))

        val issues = TimelineValidator.validate(project)

        assertTrue(issues.any { it.code == ProjectIssueCode.INVALID_TEXT_OVERLAY && it.subjectId == "caption-1" })
    }
}
