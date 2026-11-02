package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignProjectJsonV5Test {
    @Test
    fun latestSchemaRoundTripsDesignElementsCanvasAndReusableComponents() {
        val element = DesignElement(
            id = "shape-1",
            startUs = 0L,
            durationUs = 2_000_000L,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(
                    points = listOf(PathPoint(0.1, 0.2), PathPoint(0.9, 0.8)),
                    closed = true,
                ),
                fillArgb = 0xFF102030.toInt(),
                strokeArgb = 0xFFFFFFFF.toInt(),
                strokeWidthPx = 4.0,
            ),
            zIndex = 3,
        )
        val canvas = DesignCanvasSettings(
            guides = listOf(CanvasGuide("v25", GuideAxis.VERTICAL, 0.25)),
            snapToleranceNormalized = 0.02,
        )
        val component = ReusableComponent(
            id = "card",
            name = "Title card",
            elementIds = listOf("shape-1"),
            exposedProperties = setOf("shape-1.fill"),
        )
        val project = Project.create("Design", 1080, 1920, 30).copy(
            designElements = listOf(element),
            designCanvas = canvas,
            reusableComponents = listOf(component),
        )

        val encoded = ProjectJson.encode(project)
        val decoded = ProjectJson.decode(encoded)

        assertEquals(7, BuildContract.PROJECT_SCHEMA_VERSION)
        assertTrue(encoded.contains("\"schemaVersion\":7"))
        assertEquals(project.designElements, decoded.designElements)
        assertEquals(project.designCanvas, decoded.designCanvas)
        assertEquals(project.reusableComponents, decoded.reusableComponents)
    }

    @Test
    fun schemaV4MigratesDesignStateToNeutralDefaults() {
        val v4 = ProjectJson.encode(Project.create("Legacy", 1080, 1920, 30))
            .replace("\"schemaVersion\":7", "\"schemaVersion\":4")
            .replace(Regex(",\"compositionGraph\":\\{.*}$"), "}")

        val decoded = ProjectJson.decode(v4)

        assertEquals(7, decoded.schemaVersion)
        assertTrue(decoded.designElements.isEmpty())
        assertEquals(DesignCanvasSettings(), decoded.designCanvas)
        assertTrue(decoded.reusableComponents.isEmpty())
        assertTrue(decoded.designGroups.isEmpty())
    }
}
