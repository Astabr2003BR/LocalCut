package com.localcut.editor.media

import com.localcut.editor.domain.model.*
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.domain.timeline.TimelineValidator
import kotlin.math.roundToInt

object ProjectRenderPlanCompiler {
    fun compile(
        project: Project,
        mode: RenderMode,
        resolver: RenderAssetResolver,
        outputSettings: RenderOutputSettings = RenderOutputSettings(),
    ): Result<RenderPlan> = runCatching {
        val issues = TimelineValidator.validate(project)
        if (issues.isNotEmpty()) {
            throw RenderPlanException(RenderPlanErrorCode.INVALID_PROJECT, "invalid project: ${issues.first()}")
        }
        val visualTracks = project.tracks
            .filter { it.type == TrackType.VISUAL }
            .sortedWith(compareBy<Track> { it.order }.thenBy { it.id })
        val audioTracks = project.tracks
            .filter { it.type == TrackType.AUDIO }
            .sortedWith(compareBy<Track> { it.order }.thenBy { it.id })
        if (visualTracks.isEmpty()) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "render plan requires at least one visual track",
            )
        }
        val unsupportedAudioTrack = audioTracks.firstOrNull { it.collisionPolicy != TrackCollisionPolicy.EXCLUSIVE }
        if (unsupportedAudioTrack != null) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "audio mixing supports multiple EXCLUSIVE tracks; overlay audio within one track is not enabled yet: ${unsupportedAudioTrack.id}",
            )
        }
        val hasSoloAudio = audioTracks.any { it.solo }
        val audibleAudioTracks = audioTracks.filter { track ->
            !track.muted && (!hasSoloAudio || track.solo)
        }
        val durationUs = TimelineMath.durationUs(project)
        val outputFps = outputSettings.fps ?: project.fps
        val (width, height) = outputSize(project.width, project.height, mode, outputSettings)
        val assets = project.assets.associateBy { it.id }
        val visualLayers = visualTracks.flatMap { track ->
            buildVisualLayers(
                track = track,
                totalDurationUs = durationUs,
                fps = outputFps,
                assets = assets,
                resolver = resolver,
                mode = mode,
                compositionGraph = project.compositionGraph,
            )
        }
        RenderPlan(
            width = width,
            height = height,
            transformSpaceWidth = project.width,
            transformSpaceHeight = project.height,
            fps = outputFps,
            durationUs = durationUs,
            visualLayers = visualLayers,
            audioLayers = audibleAudioTracks.map { track ->
                buildSequence(track, durationUs, outputFps, assets, resolver, mode = mode)
            },
        )
    }

    private fun buildVisualLayers(
        track: Track,
        totalDurationUs: Long,
        fps: Int,
        assets: Map<String, Asset>,
        resolver: RenderAssetResolver,
        mode: RenderMode,
        compositionGraph: CompositionGraph,
    ): List<RenderSequencePlan> {
        if (track.collisionPolicy == TrackCollisionPolicy.EXCLUSIVE || track.clips.size <= 1) {
            return listOf(
                buildSequence(
                    track = track,
                    totalDurationUs = totalDurationUs,
                    fps = fps,
                    assets = assets,
                    resolver = resolver,
                    mode = mode,
                    compositionGraph = compositionGraph,
                ),
            )
        }

        val lanes = mutableListOf<MutableList<Clip>>()
        val laneEnds = mutableListOf<Long>()
        val sortedClips = track.clips.sortedWith(compareBy<Clip> { it.startUs }.thenBy { it.id })
        for (clip in sortedClips) {
            val laneIndex = laneEnds.indexOfFirst { laneEndUs -> laneEndUs <= clip.startUs }
            val clipEndUs = Math.addExact(clip.startUs, clip.durationUs)
            if (laneIndex >= 0) {
                lanes[laneIndex] += clip
                laneEnds[laneIndex] = clipEndUs
            } else {
                lanes += mutableListOf(clip)
                laneEnds += clipEndUs
            }
        }

        return lanes.mapIndexed { laneIndex, laneClips ->
            buildSequence(
                track.copy(clips = laneClips),
                totalDurationUs,
                fps,
                assets,
                resolver,
                laneIndex = laneIndex,
                mode = mode,
                compositionGraph = compositionGraph,
            )
        }
    }

    private fun buildSequence(
        track: Track,
        totalDurationUs: Long,
        fps: Int,
        assets: Map<String, Asset>,
        resolver: RenderAssetResolver,
        laneIndex: Int = 0,
        mode: RenderMode,
        compositionGraph: CompositionGraph? = null,
    ): RenderSequencePlan {
        val segments = mutableListOf<RenderSegment>()
        var cursorUs = 0L
        for (clip in track.clips.sortedWith(compareBy<Clip> { it.startUs }.thenBy { it.id })) {
            if (clip.startUs > cursorUs) segments += RenderSegment.Gap(clip.startUs - cursorUs)
            val asset = assets[clip.assetId]
                ?: throw RenderPlanException(RenderPlanErrorCode.INVALID_PROJECT, "missing asset ${clip.assetId}", clip.assetId)
            val resolvedUri = resolver.resolve(asset)
            if (resolvedUri.isFailure) {
                if (mode == RenderMode.FAST_PREVIEW) {
                    segments += RenderSegment.Gap(clip.durationUs)
                    cursorUs = Math.addExact(clip.startUs, clip.durationUs)
                    continue
                }
                throw RenderPlanException(
                    RenderPlanErrorCode.ASSET_UNAVAILABLE,
                    "asset unavailable: ${asset.id}",
                    asset.id,
                    resolvedUri.exceptionOrNull(),
                )
            }
            val uri = resolvedUri.getOrThrow()
            val composition = compositionGraph?.let { graph ->
                CompositionRenderResolver.resolve(graph, clip).getOrElse { cause ->
                    throw RenderPlanException(
                        RenderPlanErrorCode.INVALID_PROJECT,
                        "composition render resolution failed for clip ${clip.id}: ${cause.message}",
                        cause = cause,
                    )
                }
            }
            segments += RenderSegment.Media(
                clipId = clip.id,
                assetId = asset.id,
                assetType = asset.type,
                uri = uri,
                sourceMediaDurationUs = asset.sourceDurationUs ?: clip.durationUs,
                durationUs = clip.durationUs,
                sourceInUs = clip.sourceInUs,
                sourceOutUs = clip.sourceOutUs,
                transform = composition?.transform ?: clip.transform,
                opacity = composition?.opacity ?: clip.opacity,
                volume = clip.volume,
                keyframes = composition?.keyframes ?: clip.keyframes,
                propertyAnimations = clip.propertyAnimations,
                imageFrameRate = if (asset.type == AssetType.IMAGE) fps else null,
                fadeInUs = clip.fadeInUs,
                fadeOutUs = clip.fadeOutUs,
                compositing = clip.compositing,
                effects = clip.effects,
                timeMap = clip.timeMap,
                audioProcessing = clip.audioProcessing,
                compositionContainer = composition?.container,
                compositionContainerTimeline = composition?.containerTimeline,
            )
            cursorUs = Math.addExact(clip.startUs, clip.durationUs)
        }
        if (cursorUs < totalDurationUs) segments += RenderSegment.Gap(totalDurationUs - cursorUs)
        return RenderSequencePlan(
            type = track.type,
            segments = segments,
            sourceTrackId = track.id,
            sourceTrackOrder = track.order,
            sourceCollisionPolicy = track.collisionPolicy,
            laneIndex = laneIndex,
        )
    }

    private fun outputSize(
        projectWidth: Int,
        projectHeight: Int,
        mode: RenderMode,
        outputSettings: RenderOutputSettings,
    ): Pair<Int, Int> {
        val width = outputSettings.width ?: projectWidth
        val height = outputSettings.height ?: projectHeight
        if (mode != RenderMode.FAST_PREVIEW) return width to height

        val previewLongEdge = outputSettings.previewMaxLongEdge ?: FAST_PREVIEW_LONG_EDGE
        val longEdge = maxOf(width, height)
        if (longEdge <= previewLongEdge) return width to height
        val scale = previewLongEdge.toDouble() / longEdge.toDouble()
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }

    private const val FAST_PREVIEW_LONG_EDGE = 720
}
