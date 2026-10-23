package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.*
import com.localcut.editor.domain.time.TimeMapEvaluator
import com.localcut.editor.media.Media3CompositingEffectFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeToolsUiPolicyTest {
    @Test
    fun mobileSectionsAdaptToAssetTypeWithoutHidingValidToolFamilies() {
        assertEquals(
            listOf(CreativeToolsSection.VISUAL, CreativeToolsSection.TIMING, CreativeToolsSection.AUDIO),
            CreativeToolsUiPolicy.availableSections(AssetType.VIDEO),
        )
        assertEquals(
            listOf(CreativeToolsSection.VISUAL, CreativeToolsSection.TIMING),
            CreativeToolsUiPolicy.availableSections(AssetType.IMAGE),
        )
        assertEquals(
            listOf(CreativeToolsSection.TIMING, CreativeToolsSection.AUDIO),
            CreativeToolsUiPolicy.availableSections(AssetType.AUDIO),
        )
    }

    @Test
    fun mobileSectionDefaultsPreferVisualForVisualMediaAndAudioForAudioOnly() {
        assertEquals(CreativeToolsSection.VISUAL, CreativeToolsUiPolicy.defaultSection(AssetType.VIDEO))
        assertEquals(CreativeToolsSection.VISUAL, CreativeToolsUiPolicy.defaultSection(AssetType.IMAGE))
        assertEquals(CreativeToolsSection.AUDIO, CreativeToolsUiPolicy.defaultSection(AssetType.AUDIO))
    }

    @Test
    fun numericEffectUpsertReusesTypeAndPreservesOtherEffects() {
        val effects = listOf(EffectInstance("grain", "localcut.grain"))
        val once = CreativeToolsUiPolicy.setNumericEffect(effects, "localcut.color.brightness", "amount", 0.25)
        val twice = CreativeToolsUiPolicy.setNumericEffect(once, "localcut.color.brightness", "amount", -0.4)

        assertEquals(2, twice.size)
        val brightness = twice.single { it.typeId == "localcut.color.brightness" }
        assertEquals(EffectParameter.Number(-0.4), brightness.parameters["amount"])
        assertTrue(twice.any { it.typeId == "localcut.grain" })
    }

    @Test
    fun speedPresetCreatesForwardConstantMap() {
        val source = TimeMap(baseSpeed = 1.0, reverse = true, speedPoints = listOf(SpeedPoint(0L, 1.0)))
        val result = CreativeToolsUiPolicy.constantSpeed(source, 2.0)

        assertEquals(2.0, result.baseSpeed, 0.0)
        assertFalse(result.reverse)
        assertTrue(result.speedPoints.isEmpty())
        assertTrue(result.freezeSegments.isEmpty())
    }

    @Test
    fun accelerationRampIsForwardValidAndFitsItsOwnPresentationDuration() {
        val sourceRangeUs = 3_000_000L
        val result = CreativeToolsUiPolicy.accelerationRamp(sourceRangeUs)
        val durationUs = TimeMapEvaluator.presentationDurationUs(sourceRangeUs, result)

        assertFalse(result.reverse)
        assertEquals(listOf(0.5, 2.0), result.speedPoints.map { it.speed })
        assertTrue(result.speedPoints.last().offsetUs <= durationUs)
        assertTrue(result.validationIssues().isEmpty())
    }

    @Test
    fun maskPresetIsStableAndCanBeRemoved() {
        val added = CreativeToolsUiPolicy.setMaskPreset(CompositingSettings(), MaskKind.ELLIPSE)
        assertEquals(MaskKind.ELLIPSE, added.masks.single { it.id == CreativeToolsUiPolicy.UI_MASK_ID }.kind)

        val removed = CreativeToolsUiPolicy.clearMaskPreset(added)
        assertFalse(removed.masks.any { it.id == CreativeToolsUiPolicy.UI_MASK_ID })
    }

    @Test
    fun freeformMaskPresetProducesBoundedRenderablePolygon() {
        val added = CreativeToolsUiPolicy.setMaskPreset(CompositingSettings(), MaskKind.FREEFORM)
        val mask = added.masks.single { it.id == CreativeToolsUiPolicy.UI_MASK_ID }

        assertEquals(MaskKind.FREEFORM, mask.kind)
        assertTrue(mask.points.size in 3..8)
        assertTrue(mask.points.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
        assertTrue(Media3CompositingEffectFactory.canRender(added))
    }

    @Test
    fun greenScreenPresetEnablesAndDisablesChromaWithoutChangingMask() {
        val source = CreativeToolsUiPolicy.setMaskPreset(CompositingSettings(), MaskKind.RECTANGLE)
        val enabled = CreativeToolsUiPolicy.greenScreen(source, enabled = true)
        val disabled = CreativeToolsUiPolicy.greenScreen(enabled, enabled = false)

        assertTrue(enabled.chromaKey.enabled)
        assertEquals(0xFF00FF00.toInt(), enabled.chromaKey.keyArgb)
        assertFalse(disabled.chromaKey.enabled)
        assertEquals(source.masks, disabled.masks)
    }

    @Test
    fun cropLayoutPresetPreservesExplicitBounds() {
        val source = CompositingSettings(crop = CropSettings(left = 0.1, top = 0.2, right = 0.9, bottom = 0.8))
        val result = CreativeToolsUiPolicy.cropLayout(source, CropMode.FILL)

        assertEquals(CropMode.FILL, result.crop.mode)
        assertEquals(0.1, result.crop.left, 0.0)
        assertEquals(0.8, result.crop.bottom, 0.0)
    }

    @Test
    fun easingPresetPreservesValuesAndTimingWhileChangingCurve() {
        val source = listOf(
            PropertyAnimation(
                propertyId = "transform.x",
                keyframes = listOf(
                    PropertyKeyframe("a", 0L, PropertyValue.Scalar(0.0)),
                    PropertyKeyframe("b", 1_000_000L, PropertyValue.Scalar(100.0)),
                ),
            ),
        )

        val result = CreativeToolsUiPolicy.setEasingPreset(source, EasingKind.BOUNCE)

        assertEquals(source.single().keyframes.map { it.offsetUs }, result.single().keyframes.map { it.offsetUs })
        assertEquals(source.single().keyframes.map { it.value }, result.single().keyframes.map { it.value })
        assertTrue(result.single().keyframes.all { it.easingToNext.kind == EasingKind.BOUNCE })
    }

    @Test
    fun audioPresetsStayInsideValidationBounds() {
        val voice = CreativeToolsUiPolicy.voiceAudioPreset(AudioProcessingSettings())
        val loud = CreativeToolsUiPolicy.loudnessAudioPreset(voice)

        assertTrue(loud.validationIssues().isEmpty())
        assertTrue(loud.compressor != null)
        assertTrue(loud.limiter != null)
        assertTrue(loud.eqBands.isNotEmpty())
    }
}
