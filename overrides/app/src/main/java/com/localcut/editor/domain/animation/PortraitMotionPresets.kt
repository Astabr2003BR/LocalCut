package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry

/**
 * Bounded, data-only portrait animation settings.
 * Existing mouth and other video effects remain unchanged.
 * The same parameters are evaluated by preview and Transformer export.
 */
data class PortraitMotionSettings(
    val leftX: Double = 0.37,
    val rightX: Double = 0.63,
    val eyeY: Double = 0.36,
    val radiusX: Double = 0.085,
    val radiusY: Double = 0.042,
    val firstBlinkSeconds: Double = 1.4,
    val intervalSeconds: Double = 3.2,
    val durationSeconds: Double = 0.18,
    val blinkStrength: Double = 1.0,
    val swayStrength: Double = 0.0,
    val breathStrength: Double = 0.0,
) {
    fun validate() {
        require(values().all(Double::isFinite)) { "Configuração não finita" }
        require(radiusX in 0.02..0.15 && radiusY in 0.01..0.09)
        require(leftX in radiusX..(1.0 - radiusX))
        require(rightX in radiusX..(1.0 - radiusX))
        require(eyeY in radiusY..(1.0 - radiusY))
        require(rightX - leftX >= radiusX * 2.25) { "Olhos se sobrepõem" }
        require(firstBlinkSeconds in 0.10..3.0)
        require(intervalSeconds in 2.0..6.0)
        require(durationSeconds in 0.10..0.35 && durationSeconds < intervalSeconds)
        require(blinkStrength in 0.0..1.0)
        require(swayStrength in 0.0..1.0)
        require(breathStrength in 0.0..1.0)
    }

    fun values(): List<Double> = listOf(
        leftX, rightX, eyeY, radiusX, radiusY, firstBlinkSeconds,
        intervalSeconds, durationSeconds, blinkStrength, swayStrength, breathStrength,
    )
}

enum class PortraitMotionPreset(val title: String) {
    CALM("Discreto"),
    NATURAL("Natural"),
    EXPRESSIVE("Expressivo"),
}

object PortraitMotionPresets {
    const val TYPE_ID = "localcut.character.eye_blink"
    private const val STORAGE_VERSION = "portrait-motion-v1"

    fun preset(
        selection: PortraitMotionPreset,
        geometry: PortraitMotionSettings = PortraitMotionSettings(),
    ): PortraitMotionSettings = when (selection) {
        PortraitMotionPreset.CALM -> geometry.copy(
            intervalSeconds = 4.6, durationSeconds = 0.20, blinkStrength = 0.85,
            swayStrength = 0.12, breathStrength = 0.20,
        )
        PortraitMotionPreset.NATURAL -> geometry.copy(
            intervalSeconds = 3.2, durationSeconds = 0.18, blinkStrength = 1.0,
            swayStrength = 0.28, breathStrength = 0.32,
        )
        PortraitMotionPreset.EXPRESSIVE -> geometry.copy(
            intervalSeconds = 2.5, durationSeconds = 0.16, blinkStrength = 1.0,
            swayStrength = 0.52, breathStrength = 0.58,
        )
    }

    fun fromEffect(effect: EffectInstance?): PortraitMotionSettings {
        if (effect == null || effect.typeId != TYPE_ID) return PortraitMotionSettings()
        fun number(key: String, fallback: Double) =
            (effect.parameters[key] as? EffectParameter.Number)?.value ?: fallback
        fun point(key: String, fallback: EffectParameter.Point) =
            (effect.parameters[key] as? EffectParameter.Point) ?: fallback
        val left = point("leftEye", EffectParameter.Point(0.37, 0.36))
        val right = point("rightEye", EffectParameter.Point(0.63, 0.36))
        return PortraitMotionSettings(
            leftX = left.x, rightX = right.x, eyeY = left.y,
            radiusX = number("radiusX", 0.085), radiusY = number("radiusY", 0.042),
            firstBlinkSeconds = number("firstBlinkSeconds", 1.4),
            intervalSeconds = number("intervalSeconds", 3.2),
            durationSeconds = number("durationSeconds", 0.18),
            blinkStrength = number("blinkStrength", 1.0),
            swayStrength = number("swayStrength", 0.0),
            breathStrength = number("breathStrength", 0.0),
        ).also { it.validate() }
    }

    fun upsert(
        clipId: String,
        existing: List<EffectInstance>,
        settings: PortraitMotionSettings,
    ): List<EffectInstance> {
        require(clipId.isNotBlank() && clipId.length <= 256)
        settings.validate()
        require(existing.count { it.typeId == TYPE_ID } <= 1) {
            "Mais de um efeito de olhos no mesmo clipe"
        }
        val prior = existing.singleOrNull { it.typeId == TYPE_ID }
        val id = prior?.id ?: "portrait-motion-$clipId"
        require(existing.none { it.id == id && it.typeId != TYPE_ID }) {
            "Identificador de efeito duplicado"
        }
        val effect = EffectInstance(
            id = id, typeId = TYPE_ID, enabled = true,
            parameters = mapOf(
                "leftEye" to EffectParameter.Point(settings.leftX, settings.eyeY),
                "rightEye" to EffectParameter.Point(settings.rightX, settings.eyeY),
                "radiusX" to EffectParameter.Number(settings.radiusX),
                "radiusY" to EffectParameter.Number(settings.radiusY),
                "firstBlinkSeconds" to EffectParameter.Number(settings.firstBlinkSeconds),
                "intervalSeconds" to EffectParameter.Number(settings.intervalSeconds),
                "durationSeconds" to EffectParameter.Number(settings.durationSeconds),
                "blinkStrength" to EffectParameter.Number(settings.blinkStrength),
                "swayStrength" to EffectParameter.Number(settings.swayStrength),
                "breathStrength" to EffectParameter.Number(settings.breathStrength),
            ),
        )
        require(EffectRegistry.validationIssues(effect).isEmpty()) {
            "Configuração de animação incompatível com o renderizador"
        }
        return if (prior == null) existing + effect else existing.map {
            if (it.typeId == TYPE_ID) effect else it
        }
    }

    fun remove(existing: List<EffectInstance>): List<EffectInstance> =
        existing.filterNot { it.typeId == TYPE_ID }

    // Preferences contain only bounded numeric data; never code, URLs, media
    // bytes, account IDs or secrets. Invalid/old versions fail closed.
    fun serialize(settings: PortraitMotionSettings): String {
        settings.validate()
        return STORAGE_VERSION + "|" + settings.values().joinToString("|")
    }

    fun deserialize(data: String): PortraitMotionSettings? {
        if (data.length > 512) return null
        val pieces = data.split('|')
        if (pieces.size != 12 || pieces[0] != STORAGE_VERSION) return null
        val values = pieces.drop(1).map { it.toDoubleOrNull() ?: return null }
        return runCatching {
            PortraitMotionSettings(
                values[0], values[1], values[2], values[3], values[4],
                values[5], values[6], values[7], values[8], values[9], values[10],
            ).also { it.validate() }
        }.getOrNull()
    }
}
