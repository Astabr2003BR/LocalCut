package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectInstance

/** Pure composition of existing validated planners; commit once in the UI.
 * All validations complete before the caller changes project state. */
object PortraitStudioBatch {
    fun apply(
        clipId: String,
        durationUs: Long,
        existing: List<EffectInstance>,
        motion: PortraitMotionSettings,
        brows: BrowExpressionSettings,
        transcript: String? = null,
    ): List<EffectInstance> {
        require(durationUs > 0L)
        require(existing.map { it.id }.distinct().size == existing.size) {
            "Efeitos com identificadores duplicados"
        }
        val withMotion = PortraitMotionPresets.upsert(clipId, existing, motion)
        val withExpression = BrowExpressionPresets.upsert(clipId, withMotion, brows)
        return if (transcript == null) withExpression
        else GuidedVisemeOverlay.apply(durationUs, withExpression, transcript)
    }
}
