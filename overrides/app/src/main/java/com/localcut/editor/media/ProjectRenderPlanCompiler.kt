package com.localcut.editor.media

import com.localcut.editor.domain.model.*
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.domain.timeline.TimelineValidator
import kotlin.math.roundToInt

object ProjectRenderPlanCompiler {
    fun compile(project: Project, mode: RenderMode, resolver: RenderAssetResolver): Result<RenderPlan> = runCatching {
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
        if (visualTracks.isEmpty() || audioTracks.size != 1) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "render plan requires at least one visual track and exactly one audio track",
            )
        }
        val audioTrack = audioTracks.single()
        if (audioTrack.collisionPolicy != TrackCollisionPolicy.EXCLUSIVE) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "audio mixing is not enabled yet; the audio track must remain EXCLUSIVE",
            )
        }
        val durationUs = TimelineMath.durationUs(project)
        val (width, height) = outputSize(project.width, project.height, mode)
        val assets = project.assets.associateBy { it.id }
        val visualLayers = visualTracks.flatMap { track ->
            buildVisualLayers(track, durationUs, project.fps, assets, resolver)
        }
        RenderPlan(
            width = width,
            height = height,
            transformSpaceWidth = project.width,
            transformSpaceHeight = project.height,
            fps = project.fps,
            durationUs = durationUs,
            visualLayers = visualLayers,
            audioLayers = listOf(buildSequence(audioTrack, durationUs, project.fps, assets, resolver)),
        )
    }

    private fun buildVisualLayers(
        track: Track,
        totalDurationUs: Long,
        fps: Int,
        assets: Map<String, Asset>,
        resolver: RenderAssetResolver,
    ): List<RenderSequencePlan> {
        if (track.collisionPolicy == TrackCollisionPolicy.EXCLUSIVE || track.clips.size <= 1) {
            return listOf(buildSequence(track, totalDurationUs, fps, assets, resolver))
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
    ): RenderSequencePlan {
        val segments = mutableListOf<RenderSegment>()
        var cursorUs = 0L
        for (clip in track.clips.sortedWith(compareBy<Clip> { it.startUs }.thenBy { it.id })) {
            if (clip.startUs > cursorUs) segments += RenderSegment.Gap(clip.startUs - cursorUs)
            val asset = assets[clip.assetId]
                ?: throw RenderPlanException(RenderPlanErrorCode.INVALID_PROJECT, "missing asset ${clip.assetId}", clip.assetId)
            val uri = resolver.resolve(asset).getOrElse { cause ->
                throw RenderPlanException(RenderPlanErrorCode.ASSET_UNAVAILABLE, "asset unavailable: ${asset.id}", asset.id, cause)
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
                transform = clip.transform,
                opacity = clip.opacity,
                volume = clip.volume,
                keyframes = clip.keyframes,
                imageFrameRate = if (asset.type == AssetType.IMAGE) fps else null
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

    private fun outputSize(width: Int, height: Int, mode: RenderMode): Pair<Int, Int> {
        if (mode != RenderMode.FAST_PREVIEW) return width to height
        val longEdge = maxOf(width, height)
        if (longEdge <= FAST_PREVIEW_LONG_EDGE) return width to height
        val scale = FAST_PREVIEW_LONG_EDGE.toDouble() / longEdge.toDouble()
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }

    private const val FAST_PREVIEW_LONG_EDGE = 720
}
