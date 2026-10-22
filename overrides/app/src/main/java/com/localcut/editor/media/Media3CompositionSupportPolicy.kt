package com.localcut.editor.media

object Media3CompositionSupportPolicy {
    fun validate(plan: RenderPlan, mode: RenderMode): Result<Unit> = runCatching {
        if (plan.visualLayers.size > 1 && mode == RenderMode.FULL_PREVIEW) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "multitrack visual full-preview remains disabled until the rendered-preview path is selected",
            )
        }
    }
}
