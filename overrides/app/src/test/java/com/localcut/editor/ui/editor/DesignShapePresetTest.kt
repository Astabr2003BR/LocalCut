package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignShapePresetTest {
    @Test
    fun vectorPresetsCreateClosedNormalizedRenderablePaths() {
        val base = Project.create("Shapes", 1080, 1920, 30)

        DesignToolsUiPolicy.shapePresets().forEach { preset ->
            val element = DesignToolsUiPolicy.newShapePreset(base, preset, 750_000L)
            val shape = requireNotNull(element.shape)
            val path = requireNotNull(shape.path)

            assertEquals(ShapeKind.PATH, shape.kind)
            assertTrue(path.closed)
            assertTrue(path.points.size >= 4)
            assertTrue(path.points.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
            assertEquals(750_000L, element.startUs)
            assertTrue(element.validationIssues().isEmpty())
        }
    }

    @Test
    fun presetIdsAndZIndexAdvanceAgainstExistingElements() {
        val first = DesignToolsUiPolicy.newShapePreset(
            Project.create("Shapes", 1080, 1920, 30),
            DesignShapePreset.STAR,
            0L,
        ).copy(zIndex = 7)
        val project = Project.create("Shapes", 1080, 1920, 30).copy(designElements = listOf(first))
        val second = DesignToolsUiPolicy.newShapePreset(project, DesignShapePreset.ARROW, 1_000_000L)

        assertEquals("shape-2", second.id)
        assertEquals(8, second.zIndex)
        assertEquals(1_000_000L, second.startUs)
    }
}
