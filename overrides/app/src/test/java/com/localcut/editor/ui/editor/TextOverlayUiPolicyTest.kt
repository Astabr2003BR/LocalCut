package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextOverlayUiPolicyTest {
    @Test
    fun newTextStartsAtPlayheadAndClampsDurationToTimelineEnd() {
        val project = projectWithDuration(10_000_000L)

        val overlay = TextOverlayUiPolicy.createNew(
            project = project,
            playheadUs = 9_500_000L,
            text = "Fim",
        )!!

        assertEquals(9_500_000L, overlay.startUs)
        assertEquals(500_000L, overlay.durationUs)
    }

    @Test
    fun newTextAtTimelineEndGetsOneFrameInsideTimeline() {
        val project = projectWithDuration(1_000_000L).copy(fps = 30)

        val overlay = TextOverlayUiPolicy.createNew(
            project = project,
            playheadUs = 1_000_000L,
            text = "Último frame",
        )!!

        assertEquals(966_667L, overlay.startUs)
        assertEquals(33_333L, overlay.durationUs)
    }

    @Test
    fun newTextUsesDeterministicFreeIdAndTopZIndex() {
        val project = projectWithDuration(5_000_000L).copy(
            textOverlays = listOf(
                TextOverlay("text-1", 0L, 1_000_000L, "A", zIndex = 3),
                TextOverlay("text-2", 0L, 1_000_000L, "B", zIndex = 8),
            ),
        )

        val overlay = TextOverlayUiPolicy.createNew(project, 0L, "Novo")!!

        assertEquals("text-3", overlay.id)
        assertEquals(9, overlay.zIndex)
        assertEquals(3_000_000L, overlay.durationUs)
    }

    @Test
    fun editedDurationClampsToRemainingTimelineWithoutChangingOtherProperties() {
        val project = projectWithDuration(5_000_000L)
        val original = TextOverlay(
            id = "title",
            startUs = 4_500_000L,
            durationUs = 500_000L,
            text = "Título",
            style = TextStyle(fontSizePx = 80.0, fillArgb = 0xFFFFFF00.toInt()),
            transform = TransformValue(x = 10.0, y = 20.0, scale = 1.5, rotationDeg = 5.0),
            opacity = 0.7,
            zIndex = 9,
        )

        val updated = TextOverlayUiPolicy.withDuration(project, original, 3_000_000L)!!

        assertEquals(500_000L, updated.durationUs)
        assertEquals(original.copy(durationUs = 500_000L), updated)
    }

    @Test
    fun invalidRequestedDurationDoesNotProduceInvalidOverlay() {
        val project = projectWithDuration(5_000_000L)
        val original = TextOverlay("title", 0L, 1_000_000L, "Título")

        assertNull(TextOverlayUiPolicy.withDuration(project, original, 0L))
    }

    @Test
    fun noMediaTimelineDoesNotCreateInvisibleText() {
        val project = Project.create("Vazio", 1080, 1920, 30)

        assertNull(TextOverlayUiPolicy.createNew(project, 0L, "Sem vídeo"))
    }

    private fun projectWithDuration(durationUs: Long): Project {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = durationUs,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        val clip = Clip(
            id = "clip",
            assetId = asset.id,
            startUs = 0L,
            durationUs = durationUs,
            sourceInUs = 0L,
            sourceOutUs = durationUs,
        )
        return Project.create("UI", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
