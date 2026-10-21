package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderOutputSettingsTest {
    private val resolver = RenderAssetResolver { asset -> Result.success(asset.uri) }

    @Test
    fun exportSettingsOverrideCanvasAndFpsWithoutChangingTransformSpace() {
        val project = imageProject()

        val plan = ProjectRenderPlanCompiler.compile(
            project = project,
            mode = RenderMode.EXPORT,
            resolver = resolver,
            outputSettings = RenderOutputSettings(
                width = 720,
                height = 1280,
                fps = 24,
            ),
        ).getOrThrow()

        assertEquals(720, plan.width)
        assertEquals(1280, plan.height)
        assertEquals(24, plan.fps)
        assertEquals(project.width, plan.transformSpaceWidth)
        assertEquals(project.height, plan.transformSpaceHeight)
        val media = plan.visual.segments.filterIsInstance<RenderSegment.Media>().single()
        assertEquals(24, media.imageFrameRate)
    }

    @Test
    fun adaptivePreviewLongEdgeUsesRequestedTierAndPreservesAspectRatio() {
        val project = imageProject()

        val plan = ProjectRenderPlanCompiler.compile(
            project = project,
            mode = RenderMode.FAST_PREVIEW,
            resolver = resolver,
            outputSettings = RenderOutputSettings(previewMaxLongEdge = 540),
        ).getOrThrow()

        assertEquals(304, plan.width)
        assertEquals(540, plan.height)
        assertEquals(project.fps, plan.fps)
        assertEquals(project.width, plan.transformSpaceWidth)
        assertEquals(project.height, plan.transformSpaceHeight)
    }

    private fun imageProject(): Project {
        val asset = Asset(
            id = "image-1",
            type = AssetType.IMAGE,
            uri = "content://image/1",
            sourceDurationUs = null,
            sizeBytes = 1L,
            displayName = "image.jpg",
            mimeType = "image/jpeg",
            fingerprint = "image-1",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L,
        )
        val base = Project.create("Preview", 1080, 1920, 30)
        return base.copy(
            assets = listOf(asset),
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") track.copy(clips = listOf(clip)) else track
            },
        )
    }
}
