package com.localcut.editor.domain.model

import com.localcut.editor.domain.design.CaptionEditor
import com.localcut.editor.domain.design.SnapEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignElementTest {
    @Test
    fun legacyTextDefaultsRemainVisuallyNeutral() {
        val style = TextStyle()
        val overlay = TextOverlay("t", 0L, 1_000_000L, "LocalCut")

        assertEquals(null, style.font)
        assertEquals(0.0, style.letterSpacingEm, 0.0)
        assertEquals(1.0, style.lineSpacingMultiplier, 0.0)
        assertEquals(null, style.background)
        assertTrue(style.strokes.isEmpty())
        assertTrue(style.shadows.isEmpty())
        assertEquals(TextAnimationKind.NONE, overlay.entrance.kind)
        assertEquals(TextAnimationKind.NONE, overlay.exit.kind)
        assertEquals(TextAnimationKind.NONE, overlay.loop.kind)
    }

    @Test
    fun captionSplitKeepsStyleAndProducesContiguousTiming() {
        val overlay = TextOverlay(
            id = "caption-1",
            startUs = 1_000_000L,
            durationUs = 2_000_000L,
            text = "hello world",
            style = TextStyle(letterSpacingEm = 0.05),
            caption = CaptionMetadata(languageTag = "pt-BR", speaker = "A"),
        )

        val (left, right) = CaptionEditor.split(overlay, 750_000L, "caption-2", "hello", "world")

        assertEquals(1_000_000L, left.startUs)
        assertEquals(750_000L, left.durationUs)
        assertEquals(1_750_000L, right.startUs)
        assertEquals(1_250_000L, right.durationUs)
        assertEquals(left.style, right.style)
        assertEquals(left.caption?.languageTag, right.caption?.languageTag)
    }

    @Test
    fun snapEngineSnapsToCenterAndGuidesWithinTolerance() {
        val settings = DesignCanvasSettings(
            guides = listOf(CanvasGuide("g1", GuideAxis.VERTICAL, 0.25)),
            snapToleranceNormalized = 0.02,
        )

        assertEquals(0.5, SnapEngine.snapX(0.51, settings), 0.0)
        assertEquals(0.25, SnapEngine.snapX(0.26, settings), 0.0)
        assertEquals(0.80, SnapEngine.snapX(0.80, settings), 0.0)
    }

    @Test
    fun vectorElementValidationRejectsInvalidPathAndTiming() {
        val element = DesignElement(
            id = "shape-1",
            startUs = -1L,
            durationUs = 0L,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(listOf(PathPoint(Double.NaN, 0.0))),
            ),
        )

        val issues = element.validationIssues()

        assertTrue(issues.any { "timing" in it })
        assertTrue(issues.any { "finite" in it })
    }

    @Test
    fun reusableComponentRequiresExistingElementIds() {
        val component = ReusableComponent(
            id = "card",
            name = "Title card",
            elementIds = listOf("shape", "missing"),
            exposedProperties = setOf("title.text", "shape.fill"),
        )

        assertTrue(component.validationIssues(setOf("shape")).any { "missing" in it })
    }
}
