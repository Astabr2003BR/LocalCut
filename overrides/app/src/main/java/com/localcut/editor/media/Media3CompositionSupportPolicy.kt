package com.localcut.editor.media

object Media3CompositionSupportPolicy {
    fun validate(plan: RenderPlan, mode: RenderMode): Result<Unit> = runCatching {
        if (plan.visualLayers.size > 1 && mode != RenderMode.EXPORT) {
            throw RenderPlanException(
                RenderPlanErrorCode.UNSUPPORTED_TRACK_LAYOUT,
                "multitrack visual preview is temporarily disabled because Media3 CompositionPlayer has unresolved overlap reliability issues",
            )
        }
    }
}
