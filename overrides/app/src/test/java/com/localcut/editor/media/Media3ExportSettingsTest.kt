package com.localcut.editor.media

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ExportSettingsTest {
    @Test
    fun h264ProfileMapsToExactRenderSettingsAndAvcMimeType() {
        val requested = ExportProfile(1920, 1080, 30, 8_000_000, ExportVideoCodec.H264)

        val settings = Media3ExportSettingsResolver.resolve(
            requested,
            ExportCapabilities(hevcEncoderAvailable = false),
        ).getOrThrow()

        assertEquals(requested, settings.effectiveProfile)
        assertEquals(RenderOutputSettings.forExport(requested), settings.outputSettings)
        assertEquals(MimeTypes.VIDEO_H264, settings.videoMimeType)
        assertFalse(settings.usedCodecFallback)
    }

    @Test
    fun supportedHevcMapsToHevcMimeType() {
        val requested = ExportProfile(1280, 720, 30, 4_000_000, ExportVideoCodec.HEVC)

        val settings = Media3ExportSettingsResolver.resolve(
            requested,
            ExportCapabilities(hevcEncoderAvailable = true),
        ).getOrThrow()

        assertEquals(MimeTypes.VIDEO_H265, settings.videoMimeType)
        assertEquals(ExportVideoCodec.HEVC, settings.effectiveProfile.codec)
        assertFalse(settings.usedCodecFallback)
    }

    @Test
    fun unsupportedHevcFallsBackBeforeMedia3IsConfigured() {
        val requested = ExportProfile(1280, 720, 30, 4_000_000, ExportVideoCodec.HEVC)

        val settings = Media3ExportSettingsResolver.resolve(
            requested,
            ExportCapabilities(hevcEncoderAvailable = false),
        ).getOrThrow()

        assertEquals(MimeTypes.VIDEO_H264, settings.videoMimeType)
        assertEquals(ExportVideoCodec.H264, settings.effectiveProfile.codec)
        assertTrue(settings.usedCodecFallback)
    }
}
