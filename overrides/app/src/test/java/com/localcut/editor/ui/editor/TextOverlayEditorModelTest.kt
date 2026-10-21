package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextHorizontalAlignment
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.TextStyle
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayEditorModelTest {
    @Test
    fun newDraftKeepsPolicyTimingButStartsWithEmptyEditableText() {
        val project = projectWithDuration(10_000_000L)

        val model = TextOverlayEditorModel.forNew(project, 9_500_000L)!!

        assertTrue(model.isNew)
        assertEquals("", model.draft.text)
        assertEquals(9_500_000L, model.draft.startUs)
        assertEquals(500_000L, model.draft.durationUs)
        assertNull(model.overlayForSave())
    }

    @Test
    fun editingVisibleTextPropertiesPreservesNonUiProperties() {
        val original = TextOverlay(
            id = "title",
            startUs = 1_000_000L,
            durationUs = 2_000_000L,
            text = "Antes",
            style = TextStyle(
                fontSizePx = 64.0,
                alignment = TextHorizontalAlignment.START,
                fillArgb = 0xFFFFFFFF.toInt(),
                strokeArgb = 0xFF000000.toInt(),
                strokeWidthPx = 3.0,
                shadowArgb = 0xAA000000.toInt(),
                shadowRadiusPx = 5.0,
                shadowOffsetX = 2.0,
                shadowOffsetY = 4.0,
            ),
            transform = TransformValue(x = 20.0, y = 40.0, scale = 1.2, rotationDeg = 7.0),
            opacity = 0.75,
            zIndex = 12,
        )

        val edited = TextOverlayEditorModel.forEdit(original)
            .withText("Depois")
            .withFontSizePx(80.0)
            .withAlignment(TextHorizontalAlignment.END)
            .withFillArgb(0xFFFFFF00.toInt())
            .draft

        assertEquals("Depois", edited.text)
        assertEquals(80.0, edited.style.fontSizePx, 0.0)
        assertEquals(TextHorizontalAlignment.END, edited.style.alignment)
        assertEquals(0xFFFFFF00.toInt(), edited.style.fillArgb)
        assertEquals(original.style.strokeArgb, edited.style.strokeArgb)
        assertEquals(original.style.strokeWidthPx, edited.style.strokeWidthPx, 0.0)
        assertEquals(original.style.shadowArgb, edited.style.shadowArgb)
        assertEquals(original.style.shadowRadiusPx, edited.style.shadowRadiusPx, 0.0)
        assertEquals(original.transform, edited.transform)
        assertEquals(original.opacity, edited.opacity, 0.0)
        assertEquals(original.zIndex, edited.zIndex)
    }

    @Test
    fun durationPresetUsesTimelineClampAndValidTextCanBeSaved() {
        val project = projectWithDuration(5_000_000L)
        val original = TextOverlay("title", 4_500_000L, 500_000L, "Título")

        val model = TextOverlayEditorModel.forEdit(original)
            .withDuration(project, 3_000_000L)
            .withText("Título editado")

        assertEquals(500_000L, model.draft.durationUs)
        assertFalse(model.isNew)
        assertEquals(model.draft, model.overlayForSave())
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
        val clip = Clip("clip", asset.id, 0L, durationUs, 0L, durationUs)
        return Project.create("UI", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
