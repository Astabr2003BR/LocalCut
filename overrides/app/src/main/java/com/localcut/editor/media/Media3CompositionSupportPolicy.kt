package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.TimeMap

object Media3CompositionSupportPolicy {
    fun validate(plan: RenderPlan, mode: RenderMode): Result<Unit> = runCatching {
        if (plan.visualLayers.size > 1 && mode == RenderMode.FULL_PREVIEW) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "multitrack visual full-preview remains disabled until the rendered-preview path is selected",
            )
        }

        val visualMediaSegments = plan.visualLayers
            .asSequence()
            .flatMap { it.segments.asSequence() }
            .filterIsInstance<RenderSegment.Media>()
            .toList()
        val audioMediaSegments = plan.audioLayers
            .asSequence()
            .flatMap { it.segments.asSequence() }
            .filterIsInstance<RenderSegment.Media>()
            .toList()
        val mediaSegments = visualMediaSegments + audioMediaSegments

        val unsupportedCompositing = mediaSegments.firstOrNull { segment ->
            segment.assetType != AssetType.AUDIO && !Media3CompositingEffectFactory.canRender(segment.compositing)
        }
        if (unsupportedCompositing != null) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_COMPOSITING,
                "clip compositing requires a Media3 backend that is not enabled yet: ${unsupportedCompositing.clipId}",
            )
        }

        val unsupportedEffect = mediaSegments.firstOrNull { segment ->
            segment.effects.any { effect ->
                effect.enabled && (segment.assetType == AssetType.AUDIO || !Media3EffectStackFactory.canRender(effect))
            }
        }
        if (unsupportedEffect != null) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_EFFECT,
                "clip has an effect without an enabled Media3 backend: ${unsupportedEffect.clipId}",
            )
        }

        val unsupportedVisualTimeMap = visualMediaSegments.firstOrNull { segment ->
            val map = segment.timeMap
            when {
                map == TimeMap() -> false
                segment.assetType == AssetType.IMAGE -> true
                map.freezeSegments.isNotEmpty() -> !Media3FreezeFramePlanner.canRender(
                    assetType = segment.assetType,
                    timeMap = map,
                    fadeInUs = segment.fadeInUs,
                    fadeOutUs = segment.fadeOutUs,
                    sourceRangeUs = segment.sourceOutUs - segment.sourceInUs,
                    presentationDurationUs = segment.durationUs,
                )
                else -> !Media3TimeMapSupport.canRender(map)
            }
        }
        val unsupportedAudioTimeMap = audioMediaSegments.firstOrNull { segment ->
            segment.timeMap != TimeMap() && !Media3TimeMapSupport.canRender(segment.timeMap)
        }
        val unsupportedTimeMap = unsupportedVisualTimeMap ?: unsupportedAudioTimeMap
        if (unsupportedTimeMap != null) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TIME_MAP,
                "clip time map requires a renderer combination that is not enabled yet: ${unsupportedTimeMap.clipId}",
            )
        }
    }
}
