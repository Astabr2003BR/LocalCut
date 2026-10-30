package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter

object ColorToolsUiPolicy {
    fun setNumber(
        effects: List<EffectInstance>,
        typeId: String,
        parameterName: String,
        value: Double,
    ): List<EffectInstance> = CreativeToolsUiPolicy.setNumericEffect(
        effects = effects,
        typeId = typeId,
        parameterName = parameterName,
        value = value,
    )

    fun number(
        effects: List<EffectInstance>,
        typeId: String,
        parameterName: String,
        default: Double,
    ): Double = (effects.firstOrNull { it.typeId == typeId }
        ?.parameters
        ?.get(parameterName) as? EffectParameter.Number)
        ?.value
        ?: default

    fun resetColorEffects(effects: List<EffectInstance>): List<EffectInstance> =
        effects.filterNot { it.typeId.startsWith("localcut.color.") }
}
