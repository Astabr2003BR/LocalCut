package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.ChromaKeySettings
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.CompressorSettings
import com.localcut.editor.domain.model.CropMode
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.EqBand
import com.localcut.editor.domain.model.LimiterSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskPoint
import com.localcut.editor.domain.model.MaskSpec
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import kotlin.math.roundToLong

enum class CreativeToolsSection {
    VISUAL,
    TIMING,
    AUDIO,
}

object CreativeToolsUiPolicy {
    const val UI_MASK_ID = "ui-primary-mask"

    fun availableSections(assetType: AssetType?): List<CreativeToolsSection> = buildList {
        if (assetType != AssetType.AUDIO) add(CreativeToolsSection.VISUAL)
        add(CreativeToolsSection.TIMING)
        if (assetType != AssetType.IMAGE) add(CreativeToolsSection.AUDIO)
    }

    fun defaultSection(assetType: AssetType?): CreativeToolsSection =
        if (assetType == AssetType.AUDIO) CreativeToolsSection.AUDIO else CreativeToolsSection.VISUAL

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
            reverse = false,
            speedPoints = emptyList(),
            freezeSegments = emptyList(),
        )
    }

    fun accelerationRamp(sourceRangeUs: Long): TimeMap {
        require(sourceRangeUs > 0L) { "source range must be positive" }
        val rampEndUs = minOf(1_000_000L, maxOf(1L, sourceRangeUs / 3L))
        return TimeMap(
            baseSpeed = 0.5,
            reverse = false,
            speedPoints = listOf(
                SpeedPoint(0L, 0.5),
                SpeedPoint(rampEndUs, 2.0),
            ),
            freezeSegments = emptyList(),
        )
    }

    fun customSpeedCurve(sourceRangeUs: Long, speeds: List<Double>): TimeMap {
        require(sourceRangeUs > 0L) { "source range must be positive" }
        require(speeds.size >= 2) { "custom speed curve requires at least two control points" }
        require(speeds.all { it.isFinite() && it in 0.1..10.0 }) {
            "custom speed curve values must be finite and between 0.1x and 10x"
        }

        val segmentCount = speeds.lastIndex
        val normalizedIntegral = speeds.zipWithNext().sumOf { (left, right) ->
            (left + right) * 0.5
        } / segmentCount.toDouble()
        require(normalizedIntegral.isFinite() && normalizedIntegral > 0.0) {
            "custom speed curve integral must be finite and positive"
        }

        val presentationDurationUs = (sourceRangeUs.toDouble() / normalizedIntegral).roundToLong()
        require(presentationDurationUs >= segmentCount.toLong()) {
            "custom speed curve is shorter than microsecond timing resolution"
        }

        val points = speeds.mapIndexed { index, speed ->
            val offsetUs = if (index == segmentCount) {
                presentationDurationUs
            } else {
                (presentationDurationUs.toDouble() * index.toDouble() / segmentCount.toDouble()).roundToLong()
            }
            SpeedPoint(offsetUs = offsetUs, speed = speed)
        }
        require(points.zipWithNext().all { (left, right) -> right.offsetUs > left.offsetUs }) {
            "custom speed curve points must be strictly increasing"
        }

        return TimeMap(
            baseSpeed = speeds.first(),
            reverse = false,
            speedPoints = points,
            freezeSegments = emptyList(),
        )
    }

    fun customSpeedControlValues(source: TimeMap, pointCount: Int = 5): List<Double> {
        require(pointCount >= 2) { "custom speed controls require at least two points" }
        val reusable = source.speedPoints.takeIf {
            !source.reverse && source.freezeSegments.isEmpty() && it.size == pointCount
        }
        if (reusable != null) {
            return reusable.map { point -> point.speed.coerceIn(0.1, 10.0) }
        }
        val base = source.baseSpeed.coerceIn(0.1, 10.0)
        return List(pointCount) { base }
    }

    fun updateCustomSpeedControl(source: List<Double>, index: Int, speed: Double): List<Double> {
        require(source.size >= 2) { "custom speed controls require at least two points" }
        require(index in source.indices) { "custom speed control index is out of bounds" }
        require(speed.isFinite()) { "custom speed control value must be finite" }
        val bounded = speed.coerceIn(0.1, 10.0)
        return source.toMutableList().also { it[index] = bounded }
    }

    fun setMaskPreset(source: CompositingSettings, kind: MaskKind): CompositingSettings {
        require(kind in setOf(MaskKind.RECTANGLE, MaskKind.ELLIPSE, MaskKind.FREEFORM)) {
            "mobile mask preset requires a renderable mask kind"
        }
        val preset = when (kind) {
            MaskKind.FREEFORM -> MaskSpec(
                id = UI_MASK_ID,
                kind = kind,
                points = listOf(
                    MaskPoint(0.50, 0.10),
                    MaskPoint(0.88, 0.32),
                    MaskPoint(0.78, 0.82),
                    MaskPoint(0.22, 0.82),
                    MaskPoint(0.12, 0.32),
                ),
                featherPx = 8.0,
            )
            else -> MaskSpec(id = UI_MASK_ID, kind = kind)
        }
        val withoutUiPreset = source.masks.filterNot { it.id == UI_MASK_ID }
        return source.copy(masks = withoutUiPreset + preset)
    }

    fun clearMaskPreset(source: CompositingSettings): CompositingSettings =
        source.copy(masks = source.masks.filterNot { it.id == UI_MASK_ID })

    fun setPrimaryMaskFeather(source: CompositingSettings, featherPx: Double): CompositingSettings {
        require(featherPx.isFinite()) { "mask feather must be finite" }
        return updatePrimaryMask(source) { mask ->
            mask.copy(featherPx = featherPx.coerceIn(0.0, 200.0))
        }
    }

    fun setPrimaryMaskExpansion(source: CompositingSettings, expansionPx: Double): CompositingSettings {
        require(expansionPx.isFinite()) { "mask expansion must be finite" }
        return updatePrimaryMask(source) { mask ->
            mask.copy(expansionPx = expansionPx.coerceIn(-200.0, 200.0))
        }
    }

    fun setPrimaryMaskOpacity(source: CompositingSettings, opacity: Double): CompositingSettings {
        require(opacity.isFinite()) { "mask opacity must be finite" }
        return updatePrimaryMask(source) { mask ->
            mask.copy(opacity = opacity.coerceIn(0.0, 1.0))
        }
    }

    fun setPrimaryMaskInverted(source: CompositingSettings, inverted: Boolean): CompositingSettings =
        updatePrimaryMask(source) { mask -> mask.copy(invert = inverted) }

    fun greenScreen(source: CompositingSettings, enabled: Boolean): CompositingSettings = source.copy(
        chromaKey = if (enabled) {
            ChromaKeySettings(
                enabled = true,
                keyArgb = 0xFF00FF00.toInt(),
                threshold = 0.25,
                softness = 0.12,
                spillSuppression = 0.35,
                edgeFeatherPx = 0.0,
            )
        } else {
            source.chromaKey.copy(enabled = false)
        },
    )

    fun setChromaThreshold(source: CompositingSettings, threshold: Double): CompositingSettings {
        require(threshold.isFinite()) { "chroma threshold must be finite" }
        return source.copy(chromaKey = source.chromaKey.copy(threshold = threshold.coerceIn(0.0, 1.0)))
    }

    fun setChromaSoftness(source: CompositingSettings, softness: Double): CompositingSettings {
        require(softness.isFinite()) { "chroma softness must be finite" }
        return source.copy(chromaKey = source.chromaKey.copy(softness = softness.coerceIn(0.0, 1.0)))
    }

    fun setChromaSpillSuppression(source: CompositingSettings, spillSuppression: Double): CompositingSettings {
        require(spillSuppression.isFinite()) { "chroma spill suppression must be finite" }
        return source.copy(
            chromaKey = source.chromaKey.copy(spillSuppression = spillSuppression.coerceIn(0.0, 1.0)),
        )
    }

    fun cropLayout(source: CompositingSettings, mode: CropMode): CompositingSettings =
        source.copy(crop = source.crop.copy(mode = mode))

    fun setEasingPreset(
        source: List<PropertyAnimation>,
        kind: EasingKind,
    ): List<PropertyAnimation> = source.map { animation ->
        animation.copy(
            keyframes = animation.keyframes.map { keyframe ->
                keyframe.copy(easingToNext = Easing(kind = kind))
            },
        )
    }

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

    private fun updatePrimaryMask(
        source: CompositingSettings,
        transform: (MaskSpec) -> MaskSpec,
    ): CompositingSettings {
        val index = source.masks.indexOfFirst { it.id == UI_MASK_ID }
        if (index < 0) return source
        val masks = source.masks.toMutableList()
        masks[index] = transform(masks[index])
        return source.copy(masks = masks)
    }

    private fun uiEffectId(typeId: String): String =
        "ui-${typeId.removePrefix("localcut.").replace('.', '-')}"
}
