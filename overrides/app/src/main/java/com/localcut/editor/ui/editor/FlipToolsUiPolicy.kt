package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance

object FlipToolsUiPolicy {
    const val HORIZONTAL_TYPE_ID = "localcut.flip.horizontal"
    const val VERTICAL_TYPE_ID = "localcut.flip.vertical"

    fun isHorizontalEnabled(effects: List<EffectInstance>): Boolean =
        effects.any { it.enabled && it.typeId == HORIZONTAL_TYPE_ID }

    fun isVerticalEnabled(effects: List<EffectInstance>): Boolean =
        effects.any { it.enabled && it.typeId == VERTICAL_TYPE_ID }

    fun toggleHorizontal(effects: List<EffectInstance>): List<EffectInstance> =
        toggle(effects, HORIZONTAL_TYPE_ID, "ui-flip-horizontal")

    fun toggleVertical(effects: List<EffectInstance>): List<EffectInstance> =
        toggle(effects, VERTICAL_TYPE_ID, "ui-flip-vertical")

    fun clear(effects: List<EffectInstance>): List<EffectInstance> = effects.filterNot { effect ->
        effect.typeId == HORIZONTAL_TYPE_ID || effect.typeId == VERTICAL_TYPE_ID
    }

    private fun toggle(
        effects: List<EffectInstance>,
        typeId: String,
        id: String,
    ): List<EffectInstance> {
        val existingIndex = effects.indexOfFirst { it.typeId == typeId }
        if (existingIndex < 0) {
            return effects + EffectInstance(id = id, typeId = typeId)
        }
        return effects.toMutableList().also { it.removeAt(existingIndex) }
    }
}
