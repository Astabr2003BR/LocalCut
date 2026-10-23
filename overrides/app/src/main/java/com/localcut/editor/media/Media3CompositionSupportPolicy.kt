package com.localcut.editor.media

import com.localcut.editor.domain.model.CompositingSettings

object Media3CompositionSupportPolicy {
    fun validate(plan: RenderPlan, mode: RenderMode): Result<Unit> = runCatching {
        if (plan.visualLayers.size > 1 && mode == RenderMode.FULL_PREVIEW) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "multitrack visual full-preview remains disabled until the rendered-preview path is selected",
            )
        }
        val unsupported = plan.visualLayers
            .asSequence()
            .flatMap { it.segments.asSequence() }
            .filterIsInstance<RenderSegment.Media>()
            .firstOrNull { it.compositing != CompositingSettings() }
        if (unsupported != null) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_COMPOSITING,
                "clip compositing is preserved in the render plan but its GPU backend is not enabled yet: ${unsupported.clipId}",
            )
        }
    }
}
