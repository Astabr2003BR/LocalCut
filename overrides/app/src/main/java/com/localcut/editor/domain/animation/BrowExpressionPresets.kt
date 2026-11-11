package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry

enum class BrowEmotion(val title: String, val code: Int) {
    NEUTRAL("Neutro", 0),
    SURPRISED("Surpresa", 1),
    WORRIED("Preocupação", 2),
    DETERMINED("Determinação", 3);

    companion object {
        fun fromCode(value: Double): BrowEmotion {
            require(value.isFinite())
            return entries.singleOrNull { it.code.toDouble() == value }
                ?: throw IllegalArgumentException("Expressão inválida")
        }
    }
}

/** 0..1 normalized top-left image coordinates; no face auto-detection. */
data class BrowExpressionSettings(
    val leftX: Double = 0.37,
    val rightX: Double = 0.63,
    val browY: Double = 0.29,
    val radiusX: Double = 0.08,
    val radiusY: Double = 0.04,
    val emotion: BrowEmotion = BrowEmotion.NEUTRAL,
    val intensity: Double = 0.0,
) {
    fun validate() {
        require(listOf(leftX, rightX, browY, radiusX, radiusY, intensity).all(Double::isFinite))
        require(radiusX in 0.02..0.15 && radiusY in 0.01..0.09)
        require(leftX in radiusX..(1.0 - radiusX))
        require(rightX in radiusX..(1.0 - radiusX))
        require(browY in radiusY..(1.0 - radiusY))
        require(rightX - leftX >= 2.25 * radiusX)
        require(intensity in 0.0..1.0)
    }
}

/**
 * One scoped, reversible effect per image clip.
 * Old TALK, blink, audio and unrelated effects are never rewritten.
 */
object BrowExpressionPresets {
    const val TYPE_ID = "localcut.character.brow_expression"

    fun fromEffectOrNull(effect: EffectInstance?): BrowExpressionSettings? {
        if (effect == null || effect.typeId != TYPE_ID) return null
        return runCatching {
            require(EffectRegistry.validationIssues(effect).isEmpty())
            fun number(name: String, default: Double): Double =
                (effect.parameters[name] as? EffectParameter.Number)?.value ?: default
            fun point(name: String, x: Double, y: Double): EffectParameter.Point =
                effect.parameters[name] as? EffectParameter.Point ?: EffectParameter.Point(x, y)
            val left = point("leftBrow", 0.37, 0.29)
            val right = point("rightBrow", 0.63, 0.29)
            require(kotlin.math.abs(left.y - right.y) < 0.00001)
            BrowExpressionSettings(
                leftX = left.x, rightX = right.x, browY = left.y,
                radiusX = number("radiusX", 0.08),
                radiusY = number("radiusY", 0.04),
                emotion = BrowEmotion.fromCode(number("emotion", 0.0)),
                intensity = number("intensity", 0.0),
            ).also(BrowExpressionSettings::validate)
        }.getOrNull()
    }

    fun upsert(
        clipId: String,
        existing: List<EffectInstance>,
        settings: BrowExpressionSettings,
    ): List<EffectInstance> {
        require(clipId.isNotBlank() && clipId.length <= 256)
        settings.validate()
        require(existing.count { it.typeId == TYPE_ID } <= 1) {
            "Expressão facial duplicada"
        }
        if (settings.emotion == BrowEmotion.NEUTRAL || settings.intensity == 0.0) {
            return remove(existing)
        }
        val prior = existing.singleOrNull { it.typeId == TYPE_ID }
        val id = prior?.id ?: "brow-expression-$clipId"
        require(existing.none { it.id == id && it.typeId != TYPE_ID }) {
            "ID de efeito já utilizado"
        }
        val newEffect = EffectInstance(
            id = id, typeId = TYPE_ID, enabled = true,
            parameters = mapOf(
                "leftBrow" to EffectParameter.Point(settings.leftX, settings.browY),
                "rightBrow" to EffectParameter.Point(settings.rightX, settings.browY),
                "radiusX" to EffectParameter.Number(settings.radiusX),
                "radiusY" to EffectParameter.Number(settings.radiusY),
                "emotion" to EffectParameter.Number(settings.emotion.code.toDouble()),
                "intensity" to EffectParameter.Number(settings.intensity),
            ),
        )
        require(EffectRegistry.validationIssues(newEffect).isEmpty())
        return if (prior == null) existing + newEffect else existing.map { effect ->
            if (effect.typeId == TYPE_ID) newEffect else effect
        }
    }

    fun remove(existing: List<EffectInstance>): List<EffectInstance> =
        existing.filterNot { it.typeId == TYPE_ID }
}
