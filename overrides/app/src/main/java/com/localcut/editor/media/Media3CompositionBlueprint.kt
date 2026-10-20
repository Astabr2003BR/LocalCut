package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType

enum class Media3OutputTracks { VIDEO_ONLY, AUDIO_ONLY, AUDIO_VIDEO }

data class Media3CompositionBlueprint(
    val width: Int,
    val height: Int,
    val transformSpaceWidth: Int,
    val transformSpaceHeight: Int,
    val fps: Int,
    val durationUs: Long,
    val visual: Media3SequenceBlueprint,
    val audio: Media3SequenceBlueprint,
)

data class Media3SequenceBlueprint(
    val type: TrackType,
    val items: List<Media3BlueprintItem>,
)

sealed interface Media3BlueprintItem {
    val presentationDurationUs: Long

    data class Gap(override val presentationDurationUs: Long) : Media3BlueprintItem

    data class Media(
        val clipId: String,
        val assetId: String,
        val assetType: AssetType,
        val uri: String,
        val outputTracks: Media3OutputTracks,
        val sourceMediaDurationUs: Long,
        override val presentationDurationUs: Long,
        val sourceInUs: Long,
        val sourceOutUs: Long,
        val transform: TransformValue,
        val opacity: Double,
        val volume: Double,
        val keyframes: List<TransformKeyframe>,
        val imageFrameRate: Int?,
    ) : Media3BlueprintItem
}

object Media3CompositionBlueprintCompiler {
    fun compile(plan: RenderPlan): Media3CompositionBlueprint {
        require(plan.visualLayers.size == 1) {
            "Media3 V0 blueprint does not support multiple visual layers"
        }
        require(plan.audioLayers.size == 1) {
            "Media3 V0 blueprint does not support multiple audio layers"
        }
        require(plan.visualLayers.single().sourceCollisionPolicy == TrackCollisionPolicy.EXCLUSIVE) {
            "Media3 V0 blueprint does not support OVERLAY visual tracks"
        }
        require(plan.audioLayers.single().sourceCollisionPolicy == TrackCollisionPolicy.EXCLUSIVE) {
            "Media3 V0 blueprint does not support OVERLAY audio tracks"
        }
        return Media3CompositionBlueprint(
            width = plan.width,
            height = plan.height,
            transformSpaceWidth = plan.transformSpaceWidth,
            transformSpaceHeight = plan.transformSpaceHeight,
            fps = plan.fps,
            durationUs = plan.durationUs,
            visual = compileSequence(plan.visual),
            audio = compileSequence(plan.audio),
        )
    }

    private fun compileSequence(sequence: RenderSequencePlan): Media3SequenceBlueprint =
        Media3SequenceBlueprint(
            type = sequence.type,
            items = sequence.segments.map { segment ->
                when (segment) {
                    is RenderSegment.Gap -> Media3BlueprintItem.Gap(segment.durationUs)
                    is RenderSegment.Media -> Media3BlueprintItem.Media(
                        clipId = segment.clipId,
                        assetId = segment.assetId,
                        assetType = segment.assetType,
                        uri = segment.uri,
                        outputTracks = when {
                            sequence.type == TrackType.AUDIO -> Media3OutputTracks.AUDIO_ONLY
                            segment.assetType == AssetType.VIDEO -> Media3OutputTracks.AUDIO_VIDEO
                            else -> Media3OutputTracks.VIDEO_ONLY
                        },
                        sourceMediaDurationUs = segment.sourceMediaDurationUs,
                        presentationDurationUs = segment.durationUs,
                        sourceInUs = segment.sourceInUs,
                        sourceOutUs = segment.sourceOutUs,
                        transform = segment.transform,
                        opacity = segment.opacity,
                        volume = segment.volume,
                        keyframes = segment.keyframes,
                        imageFrameRate = segment.imageFrameRate,
                    )
                }
            },
        )
}
