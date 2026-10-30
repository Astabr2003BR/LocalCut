package com.localcut.editor.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
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
import com.localcut.editor.domain.model.AssetType
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
            val transitionSpecs = TransitionRenderSpecCompiler.compile(project).getOrThrow()
            toComposition(
                blueprint = Media3CompositionBlueprintCompiler.compile(plan),
                project = project,
                transitionSpecs = transitionSpecs,
            )
        }

    private fun toComposition(
        blueprint: Media3CompositionBlueprint,
        project: Project,
        transitionSpecs: Map<String, ClipTransitionRenderSpec>,
    ): Composition {
        val visualInputLayers = Media3VisualLayerOrder.toInputOrder(blueprint.visualLayers)
            .filter { it.items.isNotEmpty() }
        val sequences = buildList {
            visualInputLayers.forEach { layer ->
                add(buildVisualSequence(layer, blueprint, transitionSpecs))
            }
            blueprint.audioLayers.forEach { layer ->
                if (layer.items.any { it is Media3BlueprintItem.Media }) {
                    add(buildAudioSequence(layer, blueprint))
                }
            }
        }
        require(sequences.isNotEmpty()) { "Composition requires at least one sequence" }

        val compositionVideoEffects = buildList<Effect> {
            addAll(
                Media3FrameRatePolicy.effectsFor(
                    sourceProjectFps = project.fps,
                    targetFps = blueprint.fps,
                )
            )
            Media3DesignOverlayEffectFactory.create(
                project = project,
                outputWidth = blueprint.width,
                outputHeight = blueprint.height,
            )?.let(::add)
            Media3TextOverlayEffectFactory.create(
                project = project,
                outputWidth = blueprint.width,
                outputHeight = blueprint.height,
            )?.let(::add)
        }
        val builder = Composition.Builder(sequences)
        if (Media3CompositorSettingsPolicy.requiresCustomSettings(visualInputLayers)) {
            builder.setVideoCompositorSettings(KeyframeVideoCompositorSettings(visualInputLayers))
        }
        if (compositionVideoEffects.isNotEmpty()) {
            builder.setEffects(Effects(emptyList(), compositionVideoEffects))
        }
        return builder.build()
    }

    private fun buildVisualSequence(
        sequence: Media3SequenceBlueprint,
        blueprint: Media3CompositionBlueprint,
        transitionSpecs: Map<String, ClipTransitionRenderSpec>,
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
        var presentationStartUs = 0L
        sequence.items.forEach { item ->
            when (item) {
                is Media3BlueprintItem.Gap -> {
                    builder.addGap(item.presentationDurationUs)
                    presentationStartUs = Math.addExact(presentationStartUs, item.presentationDurationUs)
                }
                is Media3BlueprintItem.Media -> {
                    val logicalClipStartUs = presentationStartUs
                    val pieces = Media3FreezeFramePlanner.plan(item, blueprint.fps)
                    pieces.forEach { piece ->
                        builder.addItem(
                            buildMediaItem(
                                item = item,
                                blueprint = blueprint,
                                transitionSpec = transitionSpecs[item.clipId],
                                piece = piece,
                                piecePresentationStartUs = presentationStartUs,
                                logicalClipStartUs = logicalClipStartUs,
                            ),
                        )
                        presentationStartUs = Math.addExact(presentationStartUs, piece.presentationDurationUs)
                    }
                    require(presentationStartUs == Math.addExact(logicalClipStartUs, item.presentationDurationUs)) {
                        "Media3 freeze expansion changed logical clip duration: ${item.clipId}"
                    }
                }
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
                is Media3BlueprintItem.Media -> {
                    val piece = Media3FreezeFramePlanner.plan(item, blueprint.fps).single()
                    builder.addItem(
                        buildMediaItem(
                            item = item,
                            blueprint = blueprint,
                            transitionSpec = null,
                            piece = piece,
                        ),
                    )
                }
            }
        }
        return builder.build()
    }

    private fun buildMediaItem(
        item: Media3BlueprintItem.Media,
        blueprint: Media3CompositionBlueprint,
        transitionSpec: ClipTransitionRenderSpec?,
        piece: Media3FreezePiece,
        piecePresentationStartUs: Long = 0L,
        logicalClipStartUs: Long = 0L,
    ): EditedMediaItem {
        require(piecePresentationStartUs >= piece.clipLocalStartUs) {
            "piece presentation start cannot precede clip-local start"
        }
        val effectClockOriginUs = piecePresentationStartUs - piece.clipLocalStartUs
        val mediaBuilder = MediaItem.Builder().setUri(item.uri)
        if (item.assetType == AssetType.IMAGE) {
            mediaBuilder.setImageDurationMs(piece.presentationDurationUs / 1_000L)
        } else if (piece.sourceInUs != 0L || piece.sourceOutUs != item.sourceMediaDurationUs) {
            mediaBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(piece.sourceInUs)
                    .setEndPositionUs(piece.sourceOutUs)
                    .build()
            )
        }

        val carriesAudio = piece.outputTracks != Media3OutputTracks.VIDEO_ONLY
        val audioProcessors = if (!carriesAudio) {
            emptyList()
        } else {
            buildList<AudioProcessor> {
                addAll(Media3AudioProcessingFactory.create(item.audioProcessing))
                if (item.volume != 1.0) {
                    add(GainProcessor(DefaultGainProvider.Builder(item.volume.toFloat()).build()))
                }
                if (item.duckingEnvelope.windows.isNotEmpty()) {
                    add(GainProcessor(DuckingGainProvider(item.duckingEnvelope)))
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
                            piece.presentationDurationUs - item.fadeOutUs,
                            item.fadeOutUs,
                            DefaultGainProvider.FADE_OUT_LINEAR,
                        )
                    }
                    add(GainProcessor(envelope.build()))
                }
            }
        }
        val videoEffects = if (piece.outputTracks == Media3OutputTracks.AUDIO_ONLY) {
            emptyList()
        } else {
            buildList<Effect> {
                addAll(
                    TransformEffectFactory.create(
                        transform = item.transform,
                        keyframes = item.keyframes,
                        propertyAnimations = item.propertyAnimations,
                        opacity = item.opacity,
                        outputWidth = blueprint.width,
                        outputHeight = blueprint.height,
                        transformSpaceWidth = blueprint.transformSpaceWidth,
                        transformSpaceHeight = blueprint.transformSpaceHeight,
                        presentationStartUs = effectClockOriginUs,
                        compositionContainer = item.compositionContainer,
                        compositionContainerTimeline = item.compositionContainerTimeline,
                    )
                )
                addAll(
                    Media3CompositingEffectFactory.create(
                        settings = item.compositing,
                        outputWidth = blueprint.width,
                        outputHeight = blueprint.height,
                    )
                )
                addAll(Media3EffectStackFactory.create(item.effects))
                FadeToBlackEffectFactory.create(
                    durationUs = item.presentationDurationUs,
                    spec = transitionSpec,
                    presentationStartUs = logicalClipStartUs,
                )?.let(::add)
            }
        }

        val edited = EditedMediaItem.Builder(mediaBuilder.build())
            .setDurationUs(item.sourceMediaDurationUs)
            .setEffects(Effects(audioProcessors, videoEffects))
        if (piece.outputTracks == Media3OutputTracks.VIDEO_ONLY && item.assetType == AssetType.VIDEO) {
            edited.setRemoveAudio(true)
        }
        if (item.assetType != AssetType.IMAGE) {
            val sourceRangeUs = piece.sourceOutUs - piece.sourceInUs
            Media3TimeMapSupport.providerOrNull(piece.timeMap, sourceRangeUs)?.let(edited::setSpeed)
        }
        if (item.imageFrameRate != null) edited.setFrameRate(item.imageFrameRate)
        return edited.build()
    }
}
