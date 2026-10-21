package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.Project

internal object Media3VisualLayerOrder {
    fun toInputOrder(layers: List<Media3SequenceBlueprint>): List<Media3SequenceBlueprint> =
        layers.asReversed()
}

@OptIn(UnstableApi::class)
class ProjectCompositionCompiler(
    private val assetUriResolver: AssetUriResolver,
) {
    fun compile(
        project: Project,
        mode: RenderMode,
        outputSettings: RenderOutputSettings = RenderOutputSettings(),
    ): Result<Composition> =
        ProjectRenderPlanCompiler.compile(
            project = project,
            mode = mode,
            resolver = RenderAssetResolver { asset -> assetUriResolver.resolve(asset).map { it.toString() } },
            outputSettings = outputSettings,
        ).mapCatching { plan ->
            Media3CompositionSupportPolicy.validate(plan, mode).getOrThrow()
            toComposition(
                blueprint = Media3CompositionBlueprintCompiler.compile(plan),
                sourceProjectFps = project.fps,
            )
        }

    private fun toComposition(
        blueprint: Media3CompositionBlueprint,
        sourceProjectFps: Int,
    ): Composition {
        val sequences = buildList {
            Media3VisualLayerOrder.toInputOrder(blueprint.visualLayers).forEach { layer ->
                if (layer.items.isNotEmpty()) add(buildVisualSequence(layer, blueprint))
            }
            blueprint.audioLayers.forEach { layer ->
                if (layer.items.any { it is Media3BlueprintItem.Media }) {
                    add(buildAudioSequence(layer, blueprint))
                }
            }
        }
        require(sequences.isNotEmpty()) { "Composition requires at least one sequence" }

        val compositionVideoEffects = Media3FrameRatePolicy.effectsFor(
            sourceProjectFps = sourceProjectFps,
            targetFps = blueprint.fps,
        )
        val builder = Composition.Builder(sequences)
        if (compositionVideoEffects.isNotEmpty()) {
            builder.setEffects(Effects(emptyList(), compositionVideoEffects))
        }
        return builder.build()
    }

    private fun buildVisualSequence(
        sequence: Media3SequenceBlueprint,
        blueprint: Media3CompositionBlueprint,
    ): EditedMediaItemSequence {
        val hasEmbeddedAudio = sequence.items
            .filterIsInstance<Media3BlueprintItem.Media>()
            .any { it.outputTracks == Media3OutputTracks.AUDIO_VIDEO }
        val trackTypes = if (hasEmbeddedAudio) {
            setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)
        } else {
            setOf(C.TRACK_TYPE_VIDEO)
        }
        val builder = EditedMediaItemSequence.Builder(trackTypes)
        sequence.items.forEach { item ->
            when (item) {
                is Media3BlueprintItem.Gap -> builder.addGap(item.presentationDurationUs)
                is Media3BlueprintItem.Media -> builder.addItem(buildMediaItem(item, blueprint))
            }
        }
        return builder.build()
    }

    private fun buildAudioSequence(
        sequence: Media3SequenceBlueprint,
        blueprint: Media3CompositionBlueprint,
    ): EditedMediaItemSequence {
        val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        sequence.items.forEach { item ->
            when (item) {
                is Media3BlueprintItem.Gap -> builder.addGap(item.presentationDurationUs)
                is Media3BlueprintItem.Media -> builder.addItem(buildMediaItem(item, blueprint))
            }
        }
        return builder.build()
    }

    private fun buildMediaItem(
        item: Media3BlueprintItem.Media,
        blueprint: Media3CompositionBlueprint,
    ): EditedMediaItem {
        val mediaBuilder = MediaItem.Builder().setUri(item.uri)
        if (item.assetType == com.localcut.editor.domain.model.AssetType.IMAGE) {
            mediaBuilder.setImageDurationMs(item.presentationDurationUs / 1_000L)
        } else if (item.sourceInUs != 0L || item.sourceOutUs != item.sourceMediaDurationUs) {
            mediaBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(item.sourceInUs)
                    .setEndPositionUs(item.sourceOutUs)
                    .build()
            )
        }

        val audioProcessors = buildList<AudioProcessor> {
            if (item.volume != 1.0) {
                add(GainProcessor(DefaultGainProvider.Builder(item.volume.toFloat()).build()))
            }
            if (item.fadeInUs > 0L || item.fadeOutUs > 0L) {
                val envelope = DefaultGainProvider.Builder(1f)
                if (item.fadeInUs > 0L) {
                    envelope.addFadeAt(
                        0L,
                        item.fadeInUs,
                        DefaultGainProvider.FADE_IN_LINEAR,
                    )
                }
                if (item.fadeOutUs > 0L) {
                    envelope.addFadeAt(
                        item.presentationDurationUs - item.fadeOutUs,
                        item.fadeOutUs,
                        DefaultGainProvider.FADE_OUT_LINEAR,
                    )
                }
                add(GainProcessor(envelope.build()))
            }
        }
        val videoEffects = if (item.outputTracks == Media3OutputTracks.AUDIO_ONLY) {
            emptyList()
        } else {
            TransformEffectFactory.create(
                transform = item.transform,
                keyframes = item.keyframes,
                opacity = item.opacity,
                outputWidth = blueprint.width,
                outputHeight = blueprint.height,
                transformSpaceWidth = blueprint.transformSpaceWidth,
                transformSpaceHeight = blueprint.transformSpaceHeight,
            )
        }

        val edited = EditedMediaItem.Builder(mediaBuilder.build())
            .setDurationUs(item.sourceMediaDurationUs)
            .setEffects(Effects(audioProcessors, videoEffects))
        if (item.imageFrameRate != null) edited.setFrameRate(item.imageFrameRate)
        return edited.build()
    }
}
