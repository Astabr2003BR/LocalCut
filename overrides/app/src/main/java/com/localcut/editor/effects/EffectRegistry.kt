package com.localcut.editor.effects

import com.localcut.editor.domain.animation.PropertyAnimationEvaluator
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyValue

enum class EffectCategory {
    COLOR,
    BLUR,
    DETAIL,
    LIGHT,
    TEXTURE,
    DISTORTION,
    MOTION,
}

enum class EffectParameterType { NUMBER, COLOR, BOOL, TEXT, POINT }

data class EffectParameterSpec(
    val name: String,
    val type: EffectParameterType,
    val defaultValue: EffectParameter,
    val min: Double? = null,
    val max: Double? = null,
)

data class EffectDefinition(
    val typeId: String,
    val category: EffectCategory,
    val parameters: List<EffectParameterSpec>,
) {
    val parametersByName: Map<String, EffectParameterSpec> = parameters.associateBy { it.name }
}

object EffectRegistry {
    val definitions: List<EffectDefinition> = listOf(
        numeric("localcut.color.brightness", EffectCategory.COLOR, "amount", 0.0, -1.0, 1.0),
        numeric("localcut.color.contrast", EffectCategory.COLOR, "amount", 1.0, 0.0, 2.0),
        numeric("localcut.color.exposure", EffectCategory.COLOR, "stops", 0.0, -5.0, 5.0),
        numeric("localcut.color.saturation", EffectCategory.COLOR, "amount", 1.0, 0.0, 2.0),
        numeric("localcut.color.temperature", EffectCategory.COLOR, "amount", 0.0, -1.0, 1.0),
        numeric("localcut.color.tint", EffectCategory.COLOR, "amount", 0.0, -1.0, 1.0),
        EffectDefinition(
            "localcut.color.hsl",
            EffectCategory.COLOR,
            listOf(
                numberSpec("hue", 0.0, -180.0, 180.0),
                numberSpec("saturation", 0.0, -100.0, 100.0),
                numberSpec("lightness", 0.0, -100.0, 100.0),
            ),
        ),
        numeric("localcut.blur.gaussian", EffectCategory.BLUR, "radius", 0.0, 0.0, 100.0),
        numeric("localcut.sharpen", EffectCategory.DETAIL, "amount", 0.0, 0.0, 5.0),
        EffectDefinition(
            "localcut.glow",
            EffectCategory.LIGHT,
            listOf(
                numberSpec("radius", 20.0, 0.0, 100.0),
                numberSpec("intensity", 1.0, 0.0, 5.0),
                EffectParameterSpec("color", EffectParameterType.COLOR, EffectParameter.Color(0xFFFFFFFF.toInt())),
            ),
        ),
        numeric("localcut.vignette", EffectCategory.LIGHT, "amount", 0.0, 0.0, 1.0),
        EffectDefinition(
            "localcut.grain",
            EffectCategory.TEXTURE,
            listOf(
                numberSpec("amount", 0.0, 0.0, 1.0),
                numberSpec("size", 1.0, 0.1, 10.0),
                numberSpec("seed", 0.0, 0.0, 2_147_483_647.0),
            ),
        ),
        numeric("localcut.rgb_split", EffectCategory.DISTORTION, "amount", 0.0, 0.0, 100.0),
        numeric("localcut.mosaic", EffectCategory.DISTORTION, "blockSize", 1.0, 1.0, 256.0),
        EffectDefinition(
            "localcut.ripple",
            EffectCategory.DISTORTION,
            listOf(
                numberSpec("amplitude", 0.0, 0.0, 200.0),
                numberSpec("frequency", 1.0, 0.0, 20.0),
                EffectParameterSpec("center", EffectParameterType.POINT, EffectParameter.Point(0.5, 0.5)),
            ),
        ),
        EffectDefinition(
            "localcut.displacement",
            EffectCategory.DISTORTION,
            listOf(
                numberSpec("amount", 0.0, -200.0, 200.0),
                EffectParameterSpec("center", EffectParameterType.POINT, EffectParameter.Point(0.5, 0.5)),
            ),
        ),
        EffectDefinition(
            "localcut.motion_blur",
            EffectCategory.MOTION,
            listOf(
                numberSpec("shutterAngle", 180.0, 0.0, 360.0),
                numberSpec("samples", 8.0, 1.0, 64.0),
            ),
        ),
    )

    private val byTypeId = definitions.associateBy { it.typeId }

    fun definition(typeId: String): EffectDefinition? = byTypeId[typeId]

    fun validationIssues(effect: EffectInstance): List<String> = buildList {
        addAll(effect.validationIssues())
        val definition = definition(effect.typeId) ?: return@buildList
        effect.parameters.forEach { (name, value) ->
            val spec = definition.parametersByName[name]
            if (spec == null) {
                add("unknown parameter $name for ${effect.typeId}")
            } else {
                addAll(parameterIssues(name, value, spec))
            }
        }
        effect.animations.forEach { animation ->
            addAll(animation.validationIssues())
            val spec = definition.parametersByName[animation.propertyId]
            if (spec == null) {
                add("unknown animated parameter ${animation.propertyId} for ${effect.typeId}")
            } else {
                animation.keyframes.forEach { keyframe ->
                    val animated = propertyValueToParameter(keyframe.value)
                    if (animated == null || parameterType(animated) != spec.type) {
                        add("animated parameter ${animation.propertyId} has wrong type")
                    } else {
                        addAll(parameterIssues(animation.propertyId, animated, spec))
                    }
                }
            }
        }
    }

    fun evaluate(effect: EffectInstance, offsetUs: Long): EffectInstance {
        val issues = validationIssues(effect)
        require(issues.isEmpty()) { "invalid effect ${effect.id}: ${issues.first()}" }
        val definition = definition(effect.typeId) ?: return effect
        val evaluated = definition.parameters.associate { it.name to it.defaultValue }.toMutableMap()
        evaluated.putAll(effect.parameters)
        effect.animations.forEach { animation ->
            val value = PropertyAnimationEvaluator.valueAt(animation, offsetUs)
            val parameter = requireNotNull(propertyValueToParameter(value)) {
                "unsupported animated effect parameter value"
            }
            evaluated[animation.propertyId] = parameter
        }
        return effect.copy(parameters = evaluated.toMap(), animations = emptyList())
    }

    private fun parameterIssues(
        name: String,
        value: EffectParameter,
        spec: EffectParameterSpec,
    ): List<String> = buildList {
        if (parameterType(value) != spec.type) {
            add("parameter $name has wrong type")
            return@buildList
        }
        if (value is EffectParameter.Number) {
            val min = spec.min
            val max = spec.max
            if ((min != null && value.value < min) || (max != null && value.value > max)) {
                add("parameter $name is outside allowed range")
            }
        }
    }

    private fun parameterType(value: EffectParameter): EffectParameterType = when (value) {
        is EffectParameter.Number -> EffectParameterType.NUMBER
        is EffectParameter.Color -> EffectParameterType.COLOR
        is EffectParameter.Bool -> EffectParameterType.BOOL
        is EffectParameter.Text -> EffectParameterType.TEXT
        is EffectParameter.Point -> EffectParameterType.POINT
    }

    private fun propertyValueToParameter(value: PropertyValue): EffectParameter? = when (value) {
        is PropertyValue.Scalar -> EffectParameter.Number(value.value)
        is PropertyValue.Point -> EffectParameter.Point(value.x, value.y)
        is PropertyValue.Color -> EffectParameter.Color(value.argb)
        is PropertyValue.Flag -> EffectParameter.Bool(value.value)
    }

    private fun numeric(
        typeId: String,
        category: EffectCategory,
        name: String,
        default: Double,
        min: Double,
        max: Double,
    ): EffectDefinition = EffectDefinition(typeId, category, listOf(numberSpec(name, default, min, max)))

    private fun numberSpec(name: String, default: Double, min: Double, max: Double): EffectParameterSpec =
        EffectParameterSpec(name, EffectParameterType.NUMBER, EffectParameter.Number(default), min, max)
}
