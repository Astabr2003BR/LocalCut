package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.TimeMap
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
    val visualLayers: List<Media3SequenceBlueprint>,
    val audioLayers: List<Media3SequenceBlueprint>,
) {
    val visual: Media3SequenceBlueprint
        get() = visualLayers.firstOrNull() ?: Media3SequenceBlueprint(TrackType.VISUAL, emptyList())

    val audio: Media3SequenceBlueprint
        get() = audioLayers.firstOrNull() ?: Media3SequenceBlueprint(TrackType.AUDIO, emptyList())
}

data class Media3SequenceBlueprint(
    val type: TrackType,
    val items: List<Media3BlueprintItem>,
    val sourceTrackId: String? = null,
    val sourceTrackOrder: Int = 0,
    val sourceCollisionPolicy: TrackCollisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
    val laneIndex: Int = 0,
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
        val fadeInUs: Long = 0L,
        val fadeOutUs: Long = 0L,
        val compositing: CompositingSettings = CompositingSettings(),
        val effects: List<EffectInstance> = emptyList(),
        val timeMap: TimeMap = TimeMap(),
        val audioProcessing: AudioProcessingSettings = AudioProcessingSettings(),
        val propertyAnimations: List<PropertyAnimation> = emptyList(),
        val compositionContainer: CompositionTransformContainer? = null,
    ) : Media3BlueprintItem
}

object Media3CompositionBlueprintCompiler {
    fun compile(plan: RenderPlan): Media3CompositionBlueprint {
        require(plan.visualLayers.isNotEmpty()) { "Media3 composition requires at least one visual layer" }
        return Media3CompositionBlueprint(
            width = plan.width,
            height = plan.height,
            transformSpaceWidth = plan.transformSpaceWidth,
            transformSpaceHeight = plan.transformSpaceHeight,
            fps = plan.fps,
            durationUs = plan.durationUs,
            visualLayers = plan.visualLayers.map(::compileSequence),
            audioLayers = plan.audioLayers.map(::compileSequence),
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
                        fadeInUs = segment.fadeInUs,
                        fadeOutUs = segment.fadeOutUs,
                        compositing = segment.compositing,
                        effects = segment.effects,
                        timeMap = segment.timeMap,
                        audioProcessing = segment.audioProcessing,
                        propertyAnimations = segment.propertyAnimations,
                        compositionContainer = segment.compositionContainer,
                    )
                }
            },
            sourceTrackId = sequence.sourceTrackId,
            sourceTrackOrder = sequence.sourceTrackOrder,
            sourceCollisionPolicy = sequence.sourceCollisionPolicy,
            laneIndex = sequence.laneIndex,
        )
}
