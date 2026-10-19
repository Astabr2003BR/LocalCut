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
        val visualTracks = project.tracks.filter { it.type == TrackType.VISUAL }
        val audioTracks = project.tracks.filter { it.type == TrackType.AUDIO }
        if (visualTracks.size != 1 || audioTracks.size != 1) {
            throw RenderPlanException(RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT, "V0 requires exactly one visual and one audio track")
        }
        val visualTrack = visualTracks.single()
        val audioTrack = audioTracks.single()
        if (
            visualTrack.collisionPolicy != TrackCollisionPolicy.EXCLUSIVE ||
            audioTrack.collisionPolicy != TrackCollisionPolicy.EXCLUSIVE
        ) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "V0 renderer supports only EXCLUSIVE tracks",
            )
        }
        val durationUs = TimelineMath.durationUs(project)
        val (width, height) = outputSize(project.width, project.height, mode)
        val assets = project.assets.associateBy { it.id }
        RenderPlan(
            width = width,
            height = height,
            transformSpaceWidth = project.width,
            transformSpaceHeight = project.height,
            fps = project.fps,
            durationUs = durationUs,
            visual = buildSequence(visualTrack, durationUs, project.fps, assets, resolver),
            audio = buildSequence(audioTrack, durationUs, project.fps, assets, resolver)
        )
    }

    private fun buildSequence(
        track: Track,
        totalDurationUs: Long,
        fps: Int,
        assets: Map<String, Asset>,
        resolver: RenderAssetResolver
    ): RenderSequencePlan {
        val segments = mutableListOf<RenderSegment>()
        var cursorUs = 0L
        for (clip in track.clips.sortedBy { it.startUs }) {
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
        return RenderSequencePlan(track.type, segments)
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
