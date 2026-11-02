package com.localcut.editor.media

import com.localcut.editor.data.project.ProjectJson
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasProjectParityTest {
    @Test
    fun socialCanvasSizeRoundTripsWithoutSchemaChange() {
        val source = Project.create("Canvas 4x5", 1080, 1350, 30)

        val decoded = ProjectJson.decode(ProjectJson.encode(source))

        assertEquals(source.schemaVersion, decoded.schemaVersion)
        assertEquals(1080, decoded.width)
        assertEquals(1350, decoded.height)
    }

    @Test
    fun exportAndFastPreviewKeepSameProjectAspectSemantics() {
        val project = Project.create("Canvas 4x5", 1080, 1350, 30)
        val resolver = RenderAssetResolver { Result.failure(IllegalStateException("unused for empty project")) }

        val exportPlan = ProjectRenderPlanCompiler.compile(
            project = project,
            mode = RenderMode.EXPORT,
            resolver = resolver,
        ).getOrThrow()
        val previewPlan = ProjectRenderPlanCompiler.compile(
            project = project,
            mode = RenderMode.FAST_PREVIEW,
            resolver = resolver,
        ).getOrThrow()

        assertEquals(1080, exportPlan.width)
        assertEquals(1350, exportPlan.height)
        assertEquals(1080, exportPlan.transformSpaceWidth)
        assertEquals(1350, exportPlan.transformSpaceHeight)

        assertEquals(576, previewPlan.width)
        assertEquals(720, previewPlan.height)
        assertEquals(1080, previewPlan.transformSpaceWidth)
        assertEquals(1350, previewPlan.transformSpaceHeight)
        assertTrue(previewPlan.width * exportPlan.height == previewPlan.height * exportPlan.width)
    }
}
