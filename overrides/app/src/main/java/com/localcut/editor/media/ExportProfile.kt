package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineMath

enum class ExportVideoCodec {
    H264,
    HEVC,
}

data class ExportProfile(
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrateBitsPerSecond: Int,
    val codec: ExportVideoCodec = ExportVideoCodec.H264,
) {
    companion object {
        fun defaultFor(project: Project): ExportProfile = ExportProfile(
            width = project.width,
            height = project.height,
            fps = project.fps,
            videoBitrateBitsPerSecond = defaultBitrate(project.width, project.height, project.fps),
            codec = ExportVideoCodec.H264,
        )

        private fun defaultBitrate(width: Int, height: Int, fps: Int): Int {
            val pixels = width.toLong() * height.toLong()
            return when {
                pixels > 1920L * 1080L || fps > 30 -> 16_000_000
                pixels > 1280L * 720L -> 8_000_000
                else -> 4_000_000
            }
        }
    }
}

data class ExportCapabilities(
    val hevcEncoderAvailable: Boolean = false,
)

data class ResolvedExportProfile(
    val requested: ExportProfile,
    val effective: ExportProfile,
    val usedCodecFallback: Boolean,
)

enum class ExportProfileErrorCode {
    INVALID_RESOLUTION,
    INVALID_FPS,
    INVALID_BITRATE,
    EMPTY_PROJECT,
}

class ExportProfileException(
    val code: ExportProfileErrorCode,
    message: String,
) : IllegalArgumentException(message)

object ExportProfileResolver {
    fun resolve(
        requested: ExportProfile,
        capabilities: ExportCapabilities = ExportCapabilities(),
    ): Result<ResolvedExportProfile> = runCatching {
        validate(requested)
        val shouldFallback = requested.codec == ExportVideoCodec.HEVC && !capabilities.hevcEncoderAvailable
        ResolvedExportProfile(
            requested = requested,
            effective = if (shouldFallback) requested.copy(codec = ExportVideoCodec.H264) else requested,
            usedCodecFallback = shouldFallback,
        )
    }

    internal fun validate(profile: ExportProfile) {
        if (
            profile.width !in MIN_DIMENSION..MAX_DIMENSION ||
            profile.height !in MIN_DIMENSION..MAX_DIMENSION ||
            profile.width % 2 != 0 ||
            profile.height % 2 != 0
        ) {
            throw ExportProfileException(
                ExportProfileErrorCode.INVALID_RESOLUTION,
                "export resolution must use positive even dimensions up to ${MAX_DIMENSION}px",
            )
        }
        if (profile.fps !in MIN_FPS..MAX_FPS) {
            throw ExportProfileException(
                ExportProfileErrorCode.INVALID_FPS,
                "export fps must be between $MIN_FPS and $MAX_FPS",
            )
        }
        if (profile.videoBitrateBitsPerSecond !in MIN_VIDEO_BITRATE..MAX_VIDEO_BITRATE) {
            throw ExportProfileException(
                ExportProfileErrorCode.INVALID_BITRATE,
                "export video bitrate must be between $MIN_VIDEO_BITRATE and $MAX_VIDEO_BITRATE bits/s",
            )
        }
    }

    private const val MIN_DIMENSION = 2
    private const val MAX_DIMENSION = 8192
    private const val MIN_FPS = 1
    private const val MAX_FPS = 120
    private const val MIN_VIDEO_BITRATE = 100_000
    private const val MAX_VIDEO_BITRATE = 200_000_000
}

object ExportRequestValidator {
    fun validate(project: Project, profile: ExportProfile): Result<Unit> = runCatching {
        ExportProfileResolver.validate(profile)
        val durationUs = TimelineMath.durationUs(project)
        val hasVisualMedia = project.tracks
            .asSequence()
            .filter { it.type == TrackType.VISUAL }
            .flatMap { it.clips.asSequence() }
            .any { clip -> clip.durationUs > 0L && project.assets.any { it.id == clip.assetId } }

        if (durationUs <= 0L || !hasVisualMedia) {
            throw ExportProfileException(
                ExportProfileErrorCode.EMPTY_PROJECT,
                "project has no exportable visual media",
            )
        }
    }
}
