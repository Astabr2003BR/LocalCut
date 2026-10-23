package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectParameterType
import com.localcut.editor.effects.EffectRegistry

enum class EffectBrowserCategory {
    COLOR,
    LIGHT,
    DETAIL,
    BLUR,
    TEXTURE,
    DISTORTION,
}

data class EffectBrowserPreset(
    val category: EffectBrowserCategory,
    val label: String,
    val typeId: String,
    val parameterName: String,
    val value: Double,
)

data class EffectNumericControl(
    val name: String,
    val label: String,
    val value: Double,
    val range: ClosedFloatingPointRange<Double>,
)

object EffectBrowserUiPolicy {
    private val orderedCategories = listOf(
        EffectBrowserCategory.COLOR,
        EffectBrowserCategory.LIGHT,
        EffectBrowserCategory.DETAIL,
        EffectBrowserCategory.BLUR,
        EffectBrowserCategory.TEXTURE,
        EffectBrowserCategory.DISTORTION,
    )

    private val presets = listOf(
        EffectBrowserPreset(EffectBrowserCategory.COLOR, "Brilho +", "localcut.color.brightness", "amount", 0.2),
        EffectBrowserPreset(EffectBrowserCategory.COLOR, "Contraste +", "localcut.color.contrast", "amount", 1.2),
        EffectBrowserPreset(EffectBrowserCategory.COLOR, "Exposição +", "localcut.color.exposure", "stops", 0.5),
        EffectBrowserPreset(EffectBrowserCategory.COLOR, "Saturação +", "localcut.color.saturation", "amount", 1.25),
        EffectBrowserPreset(EffectBrowserCategory.COLOR, "Quente", "localcut.color.temperature", "amount", 0.35),
        EffectBrowserPreset(EffectBrowserCategory.COLOR, "Frio", "localcut.color.temperature", "amount", -0.35),
        EffectBrowserPreset(EffectBrowserCategory.COLOR, "Magenta", "localcut.color.tint", "amount", 0.3),
        EffectBrowserPreset(EffectBrowserCategory.LIGHT, "Glow", "localcut.glow", "radius", 10.0),
        EffectBrowserPreset(EffectBrowserCategory.LIGHT, "Vignette", "localcut.vignette", "amount", 0.55),
        EffectBrowserPreset(EffectBrowserCategory.DETAIL, "Sharpen", "localcut.sharpen", "amount", 1.0),
        EffectBrowserPreset(EffectBrowserCategory.BLUR, "Blur", "localcut.blur.gaussian", "radius", 8.0),
        EffectBrowserPreset(EffectBrowserCategory.TEXTURE, "Grain", "localcut.grain", "amount", 0.18),
        EffectBrowserPreset(EffectBrowserCategory.DISTORTION, "RGB Split", "localcut.rgb_split", "amount", 8.0),
        EffectBrowserPreset(EffectBrowserCategory.DISTORTION, "Mosaic", "localcut.mosaic", "blockSize", 12.0),
        EffectBrowserPreset(EffectBrowserCategory.DISTORTION, "Ripple", "localcut.ripple", "amplitude", 10.0),
        EffectBrowserPreset(EffectBrowserCategory.DISTORTION, "Displace", "localcut.displacement", "amount", 12.0),
    )

    fun categories(): List<EffectBrowserCategory> = orderedCategories

    fun presets(category: EffectBrowserCategory): List<EffectBrowserPreset> =
        presets.filter { it.category == category }

    fun adjustableRange(preset: EffectBrowserPreset): ClosedFloatingPointRange<Double>? =
        numericRange(preset.typeId, preset.parameterName)

    fun adjustableParameters(
        effects: List<EffectInstance>,
        preset: EffectBrowserPreset,
    ): List<EffectNumericControl> {
        val definition = EffectRegistry.definition(preset.typeId) ?: return emptyList()
        val effect = effects.firstOrNull { it.typeId == preset.typeId }
        return definition.parameters.mapNotNull { spec ->
            if (spec.type != EffectParameterType.NUMBER || spec.name == "seed") return@mapNotNull null
            val min = spec.min ?: return@mapNotNull null
            val max = spec.max ?: return@mapNotNull null
            val explicit = effect?.parameters?.get(spec.name) as? EffectParameter.Number
            val defaultValue = (spec.defaultValue as EffectParameter.Number).value
            EffectNumericControl(
                name = spec.name,
                label = parameterLabel(spec.name),
                value = explicit?.value ?: if (spec.name == preset.parameterName) preset.value else defaultValue,
                range = min..max,
            )
        }
    }

    fun currentValue(
        effects: List<EffectInstance>,
        preset: EffectBrowserPreset,
    ): Double {
        val explicit = effects
            .firstOrNull { it.typeId == preset.typeId }
            ?.parameters
            ?.get(preset.parameterName) as? EffectParameter.Number
        return explicit?.value ?: preset.value
    }

    fun setValue(
        effects: List<EffectInstance>,
        preset: EffectBrowserPreset,
        value: Double,
    ): List<EffectInstance> =
        setParameter(effects, preset, preset.parameterName, value)

    fun setParameter(
        effects: List<EffectInstance>,
        preset: EffectBrowserPreset,
        parameterName: String,
        value: Double,
    ): List<EffectInstance> {
        require(value.isFinite()) { "effect value must be finite" }
        val range = requireNotNull(numericRange(preset.typeId, parameterName)) {
            "effect ${preset.typeId}.$parameterName is not adjustable"
        }
        return CreativeToolsUiPolicy.setNumericEffect(
            effects = effects,
            typeId = preset.typeId,
            parameterName = parameterName,
            value = value.coerceIn(range.start, range.endInclusive),
        )
    }

    fun hasEffectsInCategory(effects: List<EffectInstance>, category: EffectBrowserCategory): Boolean {
        val typeIds = presets(category).mapTo(hashSetOf()) { it.typeId }
        return effects.any { it.typeId in typeIds }
    }

    fun clearCategory(
        effects: List<EffectInstance>,
        category: EffectBrowserCategory,
    ): List<EffectInstance> {
        val typeIds = presets(category).mapTo(hashSetOf()) { it.typeId }
        return effects.filterNot { it.typeId in typeIds }
    }

    fun label(category: EffectBrowserCategory): String = when (category) {
        EffectBrowserCategory.COLOR -> "Cor"
        EffectBrowserCategory.LIGHT -> "Luz"
        EffectBrowserCategory.DETAIL -> "Detalhe"
        EffectBrowserCategory.BLUR -> "Desfoque"
        EffectBrowserCategory.TEXTURE -> "Textura"
        EffectBrowserCategory.DISTORTION -> "Distorção"
    }

    private fun numericRange(typeId: String, parameterName: String): ClosedFloatingPointRange<Double>? {
        val spec = EffectRegistry.definition(typeId)
            ?.parametersByName
            ?.get(parameterName)
            ?: return null
        if (spec.type != EffectParameterType.NUMBER) return null
        val min = spec.min ?: return null
        val max = spec.max ?: return null
        return min..max
    }

    private fun parameterLabel(name: String): String = when (name) {
        "amount" -> "Intensidade"
        "radius" -> "Raio"
        "intensity" -> "Intensidade"
        "size" -> "Tamanho"
        "blockSize" -> "Bloco"
        "amplitude" -> "Amplitude"
        "frequency" -> "Frequência"
        "stops" -> "Exposição"
        else -> name
    }
}
