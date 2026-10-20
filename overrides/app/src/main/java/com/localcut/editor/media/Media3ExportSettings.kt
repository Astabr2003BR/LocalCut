package com.localcut.editor.media

import androidx.media3.common.MimeTypes

data class Media3ExportSettings(
    val requestedProfile: ExportProfile,
    val effectiveProfile: ExportProfile,
    val outputSettings: RenderOutputSettings,
    val videoMimeType: String,
    val usedCodecFallback: Boolean,
)

object Media3ExportSettingsResolver {
    fun resolve(
        requested: ExportProfile,
        capabilities: ExportCapabilities = ExportCapabilities(),
    ): Result<Media3ExportSettings> = ExportProfileResolver.resolve(requested, capabilities).map { resolved ->
        val effective = resolved.effective
        Media3ExportSettings(
            requestedProfile = requested,
            effectiveProfile = effective,
            outputSettings = RenderOutputSettings.forExport(effective),
            videoMimeType = when (effective.codec) {
                ExportVideoCodec.H264 -> MimeTypes.VIDEO_H264
                ExportVideoCodec.HEVC -> MimeTypes.VIDEO_H265
            },
            usedCodecFallback = resolved.usedCodecFallback,
        )
    }
}
