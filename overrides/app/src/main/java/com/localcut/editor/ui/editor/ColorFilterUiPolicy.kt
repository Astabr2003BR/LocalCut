package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance

enum class ColorFilterPreset(val label: String) {
    NEUTRAL("Original"),
    CLEAN("Clean"),
    CINEMA("Cinema"),
    WARM("Quente"),
    COOL("Frio"),
    MONO("Mono"),
}

object ColorFilterUiPolicy {
    fun applyPreset(
        source: List<EffectInstance>,
        preset: ColorFilterPreset,
    ): List<EffectInstance> {
        var effects = ColorToolsUiPolicy.resetColorEffects(source)
        if (preset == ColorFilterPreset.NEUTRAL) return effects

        fun number(typeId: String, parameter: String, value: Double) {
            effects = ColorToolsUiPolicy.setNumber(effects, typeId, parameter, value)
        }

        when (preset) {
            ColorFilterPreset.NEUTRAL -> Unit
            ColorFilterPreset.CLEAN -> {
                number("localcut.color.brightness", "amount", 0.04)
                number("localcut.color.contrast", "amount", 1.06)
                number("localcut.color.saturation", "amount", 1.06)
                number("localcut.color.temperature", "amount", 0.04)
            }
            ColorFilterPreset.CINEMA -> {
                number("localcut.color.contrast", "amount", 1.18)
                number("localcut.color.saturation", "amount", 0.88)
                number("localcut.color.temperature", "amount", -0.08)
                number("localcut.color.hsl", "lightness", -6.0)
            }
            ColorFilterPreset.WARM -> {
                number("localcut.color.contrast", "amount", 1.05)
                number("localcut.color.saturation", "amount", 1.08)
                number("localcut.color.temperature", "amount", 0.30)
                number("localcut.color.tint", "amount", 0.04)
            }
            ColorFilterPreset.COOL -> {
                number("localcut.color.contrast", "amount", 1.04)
                number("localcut.color.saturation", "amount", 1.02)
                number("localcut.color.temperature", "amount", -0.30)
                number("localcut.color.tint", "amount", -0.03)
            }
            ColorFilterPreset.MONO -> {
                number("localcut.color.contrast", "amount", 1.15)
                number("localcut.color.saturation", "amount", 0.0)
                number("localcut.color.hsl", "lightness", -2.0)
            }
        }
        return effects
    }
}
