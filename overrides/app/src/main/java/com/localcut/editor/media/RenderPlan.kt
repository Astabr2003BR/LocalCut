package com.localcut.editor.media

import com.localcut.editor.domain.model.*

enum class RenderMode { FAST_PREVIEW, FULL_PREVIEW, EXPORT }

enum class RenderPlanErrorCode { INVALID_PROJECT, ASSET_UNAVAILABLE, UNSUPPORTED_TRACK_LAYOUT }

class RenderPlanException(
    val code: RenderPlanErrorCode,
    message: String,
    val assetId: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

fun interface RenderAssetResolver {
    fun resolve(asset: Asset): Result<String>
}

data class RenderPlan(
    val width: Int,
    val height: Int,
    val transformSpaceWidth: Int,
    val transformSpaceHeight: Int,
    val fps: Int,
    val durationUs: Long,
    val visualLayers: List<RenderSequencePlan>,
    val audioLayers: List<RenderSequencePlan>,
) {
    val visual: RenderSequencePlan
        get() = visualLayers.firstOrNull() ?: RenderSequencePlan(TrackType.VISUAL, emptyList())

    val audio: RenderSequencePlan
        get() = audioLayers.firstOrNull() ?: RenderSequencePlan(TrackType.AUDIO, emptyList())
}

data class RenderSequencePlan(
    val type: TrackType,
    val segments: List<RenderSegment>,
    val sourceTrackId: String? = null,
    val sourceTrackOrder: Int = 0,
    val sourceCollisionPolicy: TrackCollisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
    val laneIndex: Int = 0,
)

sealed interface RenderSegment {
    val durationUs: Long

    data class Gap(override val durationUs: Long) : RenderSegment

    data class Media(
        val clipId: String,
        val assetId: String,
        val assetType: AssetType,
        val uri: String,
        val sourceMediaDurationUs: Long,
        override val durationUs: Long,
        val sourceInUs: Long,
        val sourceOutUs: Long,
        val transform: TransformValue,
        val opacity: Double,
        val volume: Double,
        val keyframes: List<TransformKeyframe>,
        val imageFrameRate: Int?,
        val fadeInUs: Long = 0L,
        val fadeOutUs: Long = 0L,
        val compositing: CompositingSettings = CompositingSettings(),
    ) : RenderSegment {
        val isImage: Boolean get() = assetType == AssetType.IMAGE
    }
}
