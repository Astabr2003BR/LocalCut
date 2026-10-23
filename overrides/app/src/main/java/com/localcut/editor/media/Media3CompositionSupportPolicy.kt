package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.TimeMap

object Media3CompositionSupportPolicy {
    fun validate(plan: RenderPlan, mode: RenderMode): Result<Unit> = runCatching {
        if (plan.visualLayers.size > 1 && mode == RenderMode.FULL_PREVIEW) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "multitrack visual full-preview remains disabled until the rendered-preview path is selected",
            )
        }
        val mediaSegments = (plan.visualLayers + plan.audioLayers)
            .asSequence()
            .flatMap { it.segments.asSequence() }
            .filterIsInstance<RenderSegment.Media>()
            .toList()

        val unsupportedCompositing = mediaSegments.firstOrNull {
            it.assetType != AssetType.AUDIO && it.compositing != CompositingSettings()
        }
        if (unsupportedCompositing != null) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_COMPOSITING,
                "clip compositing is preserved in the render plan but its GPU backend is not enabled yet: ${unsupportedCompositing.clipId}",
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

        val unsupportedTimeMap = mediaSegments.firstOrNull { segment ->
            val map = segment.timeMap
            when {
                map == TimeMap() -> false
                segment.assetType == AssetType.IMAGE -> true
                else -> !Media3TimeMapSupport.canRender(map)
            }
        }
        if (unsupportedTimeMap != null) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TIME_MAP,
                "clip time map requires ramp/reverse/freeze support that is not enabled yet: ${unsupportedTimeMap.clipId}",
            )
        }
    }
}
