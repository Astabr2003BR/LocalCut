package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Media3EncoderRequestTest {
    @Test
    fun resolvedExportSettingsCarryExactBitrateAndDisableSilentFormatFallback() {
        val requested = ExportProfile(
            width = 1920,
            height = 1080,
            fps = 30,
            videoBitrateBitsPerSecond = 12_000_000,
            codec = ExportVideoCodec.H264,
        )

        val settings = Media3ExportSettingsResolver.resolve(
            requested,
            ExportCapabilities(hevcEncoderAvailable = false),
        ).getOrThrow()

        assertEquals(12_000_000, settings.requestedVideoBitrateBitsPerSecond)
        assertFalse(settings.enableEncoderFallback)
        assertFalse(settings.enableFormatFallback)
    }

    @Test
    fun hevcFallbackUsesEffectiveH264ProfileButKeepsRequestedBitrate() {
        val requested = ExportProfile(
            width = 1280,
            height = 720,
            fps = 30,
            videoBitrateBitsPerSecond = 6_000_000,
            codec = ExportVideoCodec.HEVC,
        )

        val settings = Media3ExportSettingsResolver.resolve(
            requested,
            ExportCapabilities(hevcEncoderAvailable = false),
        ).getOrThrow()

        assertEquals(ExportVideoCodec.H264, settings.effectiveProfile.codec)
        assertEquals(6_000_000, settings.requestedVideoBitrateBitsPerSecond)
    }
}
