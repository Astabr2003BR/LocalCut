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
        numeric("localcut.color.monochrome", EffectCategory.COLOR, "amount", 1.0, 0.0, 1.0),
        numeric("localcut.color.sepia", EffectCategory.COLOR, "amount", 1.0, 0.0, 1.0),
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
            "localcut.character.mouth_warp",
            EffectCategory.DISTORTION,
            listOf(
                EffectParameterSpec("center", EffectParameterType.POINT, EffectParameter.Point(0.5, 0.65)),
                numberSpec("radiusX", 0.10, 0.01, 0.50),
                numberSpec("radiusY", 0.05, 0.005, 0.30),
                numberSpec("openness", 0.0, 0.0, 1.0),
                // Optional viseme controls. Omitted in legacy TALK projects.
                numberSpec("widthScale", 1.0, 0.60, 1.30),
                numberSpec("rounding", 0.0, 0.0, 1.0),
                numberSpec("teethVisibility", 0.0, 0.0, 1.0),
            ),
        ),
        EffectDefinition(
            "localcut.character.eye_blink",
            EffectCategory.DISTORTION,
            listOf(
                EffectParameterSpec("leftEye", EffectParameterType.POINT, EffectParameter.Point(0.37, 0.36)),
                EffectParameterSpec("rightEye", EffectParameterType.POINT, EffectParameter.Point(0.63, 0.36)),
                numberSpec("radiusX", 0.085, 0.02, 0.15),
                numberSpec("radiusY", 0.042, 0.01, 0.09),
                numberSpec("firstBlinkSeconds", 1.4, 0.10, 3.0),
                numberSpec("intervalSeconds", 3.2, 2.0, 6.0),
                numberSpec("durationSeconds", 0.18, 0.10, 0.35),
                // Values are data-only; existing projects keep the old
                // zero-idle defaults until the user selects a motion preset.
                numberSpec("blinkStrength", 1.0, 0.0, 1.0),
                numberSpec("swayStrength", 0.0, 0.0, 1.0),
                numberSpec("breathStrength", 0.0, 0.0, 1.0),
                numberSpec("headMotionStrength", 0.0, 0.0, 1.0),
                numberSpec("hairMotionStrength", 0.0, 0.0, 1.0),
                numberSpec("blinkVariation", 0.0, 0.0, 1.0),
                numberSpec("blinkAsymmetry", 0.0, 0.0, 1.0),
                numberSpec("gazeStrength", 0.0, 0.0, 1.0),
            ),
        ),
        EffectDefinition(
            "localcut.character.brow_expression",
            EffectCategory.DISTORTION,
            listOf(
                EffectParameterSpec("leftBrow", EffectParameterType.POINT,
                    EffectParameter.Point(0.37, 0.29)),
                EffectParameterSpec("rightBrow", EffectParameterType.POINT,
                    EffectParameter.Point(0.63, 0.29)),
                numberSpec("radiusX", 0.08, 0.02, 0.15),
                numberSpec("radiusY", 0.04, 0.01, 0.09),
                numberSpec("emotion", 0.0, 0.0, 3.0),
                numberSpec("intensity", 0.0, 0.0, 1.0),
            ),
        ),
        EffectDefinition("localcut.flip.horizontal", EffectCategory.DISTORTION, emptyList()),
        EffectDefinition("localcut.flip.vertical", EffectCategory.DISTORTION, emptyList()),
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
        if (effect.typeId == "localcut.character.mouth_warp") {
            val center = effect.parameters["center"] as? EffectParameter.Point
            if (center != null && (center.x !in 0.0..1.0 || center.y !in 0.0..1.0)) {
                add("parameter center is outside allowed range")
            }
        }
        if (effect.typeId == "localcut.character.brow_expression") {
            val left = effect.parameters["leftBrow"] as? EffectParameter.Point
                ?: EffectParameter.Point(0.37, 0.29)
            val right = effect.parameters["rightBrow"] as? EffectParameter.Point
                ?: EffectParameter.Point(0.63, 0.29)
            val radiusX = (effect.parameters["radiusX"] as? EffectParameter.Number)?.value ?: 0.08
            val radiusY = (effect.parameters["radiusY"] as? EffectParameter.Number)?.value ?: 0.04
            val emotion = (effect.parameters["emotion"] as? EffectParameter.Number)?.value ?: 0.0
            if (listOf(left.x, left.y, right.x, right.y, radiusX, radiusY).all(Double::isFinite)) {
                if (left.x !in radiusX..(1.0 - radiusX) ||
                    right.x !in radiusX..(1.0 - radiusX) ||
                    left.y !in radiusY..(1.0 - radiusY) ||
                    right.y !in radiusY..(1.0 - radiusY) ||
                    right.x - left.x < radiusX * 2.25 ||
                    kotlin.math.abs(left.y - right.y) > 0.00001
                ) {
                    add("eyebrow positions are invalid or overlapping")
                }
            }
            if (emotion !in listOf(0.0, 1.0, 2.0, 3.0)) {
                add("expression preset must be an integer 0..3")
            }
            if (effect.animations.isNotEmpty()) {
                add("brow expression does not yet support keyframe animations")
            }
        }
        if (effect.typeId == "localcut.character.eye_blink") {
            val left = effect.parameters["leftEye"] as? EffectParameter.Point
                ?: EffectParameter.Point(0.37, 0.36)
            val right = effect.parameters["rightEye"] as? EffectParameter.Point
                ?: EffectParameter.Point(0.63, 0.36)
            val radiusX = (effect.parameters["radiusX"] as? EffectParameter.Number)?.value ?: 0.085
            val radiusY = (effect.parameters["radiusY"] as? EffectParameter.Number)?.value ?: 0.042
            if (listOf(left.x, left.y, right.x, right.y, radiusX, radiusY).all { it.isFinite() }) {
                if (left.x !in radiusX..(1.0 - radiusX) ||
                    right.x !in radiusX..(1.0 - radiusX) ||
                    left.y !in radiusY..(1.0 - radiusY) ||
                    right.y !in radiusY..(1.0 - radiusY) ||
                    right.x - left.x < radiusX * 2.25 ||
                    kotlin.math.abs(left.y - right.y) > radiusY
                ) {
                    add("eye blink eye coordinates are outside face or overlap")
                }
            }
            if (effect.animations.isNotEmpty()) {
                add("eye blink cannot use keyed property animations")
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
