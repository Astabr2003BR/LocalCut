package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.EqBand
import com.localcut.editor.domain.model.LimiterSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskSpec
import com.localcut.editor.domain.model.TimeMap

object CreativeToolsUiPolicy {
    const val UI_MASK_ID = "ui-primary-mask"

    fun setNumericEffect(
        effects: List<EffectInstance>,
        typeId: String,
        parameterName: String,
        value: Double,
    ): List<EffectInstance> {
        require(typeId.isNotBlank()) { "effect typeId must not be blank" }
        require(parameterName.isNotBlank()) { "effect parameter name must not be blank" }
        require(value.isFinite()) { "effect value must be finite" }

        val existingIndex = effects.indexOfFirst { it.typeId == typeId }
        if (existingIndex < 0) {
            return effects + EffectInstance(
                id = uiEffectId(typeId),
                typeId = typeId,
                parameters = mapOf(parameterName to EffectParameter.Number(value)),
            )
        }

        val existing = effects[existingIndex]
        val updated = existing.copy(
            parameters = existing.parameters + (parameterName to EffectParameter.Number(value)),
        )
        return effects.toMutableList().also { it[existingIndex] = updated }
    }

    fun constantSpeed(source: TimeMap, speed: Double): TimeMap {
        require(speed.isFinite() && speed > 0.0) { "speed must be finite and positive" }
        return source.copy(
            baseSpeed = speed,
            speedPoints = emptyList(),
            freezeSegments = emptyList(),
        )
    }

    fun setMaskPreset(source: CompositingSettings, kind: MaskKind): CompositingSettings {
        val preset = MaskSpec(id = UI_MASK_ID, kind = kind)
        val withoutUiPreset = source.masks.filterNot { it.id == UI_MASK_ID }
        return source.copy(masks = withoutUiPreset + preset)
    }

    fun clearMaskPreset(source: CompositingSettings): CompositingSettings =
        source.copy(masks = source.masks.filterNot { it.id == UI_MASK_ID })

    fun voiceAudioPreset(source: AudioProcessingSettings): AudioProcessingSettings = source.copy(
        eqBands = listOf(
            EqBand(frequencyHz = 100.0, gainDb = -3.0, q = 0.8),
            EqBand(frequencyHz = 250.0, gainDb = -1.5, q = 1.0),
            EqBand(frequencyHz = 3_000.0, gainDb = 2.5, q = 1.0),
            EqBand(frequencyHz = 8_000.0, gainDb = 1.5, q = 0.8),
        ),
        compressor = CompressorSettings(
            thresholdDb = -20.0,
            ratio = 3.0,
            attackMs = 8.0,
            releaseMs = 120.0,
            makeupGainDb = 2.0,
        ),
    )

    fun loudnessAudioPreset(source: AudioProcessingSettings): AudioProcessingSettings = source.copy(
        compressor = source.compressor ?: CompressorSettings(
            thresholdDb = -18.0,
            ratio = 4.0,
            attackMs = 10.0,
            releaseMs = 120.0,
            makeupGainDb = 2.0,
        ),
        limiter = LimiterSettings(
            ceilingDb = -1.0,
            releaseMs = 80.0,
        ),
        normalizationGainDb = 3.0,
    )

    private fun uiEffectId(typeId: String): String =
        "ui-${typeId.removePrefix("localcut.").replace('.', '-')}"
}
