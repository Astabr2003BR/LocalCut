package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitMotionPresetsTest {
    private val talk = EffectInstance(
        id = "talk", typeId = "localcut.character.mouth_warp",
        parameters = mapOf(
            "center" to EffectParameter.Point(0.51, 0.68),
            "radiusX" to EffectParameter.Number(0.07),
            "radiusY" to EffectParameter.Number(0.025),
            "openness" to EffectParameter.Number(0.0),
        ),
    )

    @Test fun presetProvidesDifferentTimingWithoutChangingEyePlacement() {
        val base = PortraitMotionSettings(leftX = 0.35, rightX = 0.65)
        val calm = PortraitMotionPresets.preset(PortraitMotionPreset.CALM, base)
        val active = PortraitMotionPresets.preset(PortraitMotionPreset.EXPRESSIVE, base)
        assertTrue(calm.intervalSeconds > active.intervalSeconds)
        assertEquals(0.35, active.leftX, 0.0)
        assertEquals(0.65, calm.rightX, 0.0)
        calm.validate()
        active.validate()
    }

    @Test fun addingAndReplacingPresetKeepsTalkAndIsIdempotent() {
        val first = PortraitMotionPresets.upsert("clip-1", listOf(talk), PortraitMotionSettings())
        assertEquals(2, first.size)
        assertEquals(talk, first.first())
        assertTrue(EffectRegistry.validationIssues(first.last()).isEmpty())
        val newSettings = PortraitMotionPresets.preset(PortraitMotionPreset.NATURAL)
        val second = PortraitMotionPresets.upsert("clip-1", first, newSettings)
        assertEquals(2, second.size)
        assertEquals(talk, second.first())
        assertEquals(second, PortraitMotionPresets.upsert("clip-1", second, newSettings))
        assertEquals(listOf(talk), PortraitMotionPresets.remove(second))
        assertEquals(newSettings, PortraitMotionPresets.fromEffect(second.last()))
    }

    @Test fun serializationRoundTripsAndRejectsUnsafeData() {
        val original = PortraitMotionPresets.preset(PortraitMotionPreset.EXPRESSIVE)
        val encoded = PortraitMotionPresets.serialize(original)
        assertEquals(original, PortraitMotionPresets.deserialize(encoded))
        assertNull(PortraitMotionPresets.deserialize("bad|0|0"))
        assertNull(PortraitMotionPresets.deserialize(encoded.replace("0.37", "NaN")))
        assertNull(PortraitMotionPresets.deserialize(encoded.replace("0.37", "Infinity")))
        assertNull(PortraitMotionPresets.deserialize("a".repeat(700)))
        assertNull(PortraitMotionPresets.deserialize(encoded.replace("0.37", "0.99")))
    }

    @Test fun newHeadHairAndBlinkControlsDefaultToZeroForLegacySettings() {
        val old = PortraitMotionSettings()
        val legacy = "portrait-motion-v1|" + old.values().take(11).joinToString("|")
        assertEquals(old, PortraitMotionPresets.deserialize(legacy))
        val oldV2 = old.copy(headMotionStrength = 0.4, hairMotionStrength = 0.2,
            blinkVariation = 0.2, blinkAsymmetry = 0.1)
        val encodedV2 = "portrait-motion-v2|" + oldV2.values().take(15).joinToString("|")
        assertEquals(oldV2, PortraitMotionPresets.deserialize(encodedV2))
        val expressive = PortraitMotionPresets.preset(PortraitMotionPreset.EXPRESSIVE)
        val calm = PortraitMotionPresets.preset(PortraitMotionPreset.CALM)
        assertTrue(expressive.headMotionStrength > calm.headMotionStrength)
        assertTrue(expressive.hairMotionStrength > calm.hairMotionStrength)
        assertTrue(expressive.blinkVariation > calm.blinkVariation)
        assertTrue(expressive.blinkAsymmetry > calm.blinkAsymmetry)
        assertTrue(expressive.gazeStrength > calm.gazeStrength)
        val upgraded = PortraitMotionPresets.upsert("face", listOf(talk), expressive)
        assertEquals(expressive, PortraitMotionPresets.fromEffect(upgraded.last()))
        assertEquals(expressive, PortraitMotionPresets.deserialize(
            PortraitMotionPresets.serialize(expressive)))
        assertTrue(runCatching {
            PortraitMotionPresets.upsert("bad", listOf(talk),
                expressive.copy(headMotionStrength = Double.NaN))
        }.isFailure)
        assertTrue(runCatching {
            PortraitMotionPresets.upsert("bad-gaze", listOf(talk),
                expressive.copy(gazeStrength = Double.NaN))
        }.isFailure)
        assertNull(PortraitMotionPresets.deserialize(
            PortraitMotionPresets.serialize(expressive).replace("portrait-motion-v2", "portrait-motion-v0")))
    }

    @Test fun corruptPersistedBlinkDoesNotCrashSettingsRecovery() {
        val corrupt = EffectInstance(
            id = "eye-corrupt", typeId = PortraitMotionPresets.TYPE_ID,
            parameters = mapOf(
                "leftEye" to EffectParameter.Point(Double.NaN, 0.36),
                "rightEye" to EffectParameter.Point(0.63, 0.36),
            ),
        )
        assertNull(PortraitMotionPresets.fromPersistedEffectOrNull(corrupt))
        // A previously valid persisted effect still loads with unchanged geometry.
        val valid = PortraitMotionPresets.upsert(
            "clip-ok", listOf(talk), PortraitMotionSettings(leftX = 0.35, rightX = 0.65),
        ).last()
        assertEquals(0.35, PortraitMotionPresets.fromPersistedEffectOrNull(valid)?.leftX ?: -1.0, 0.0)
        assertEquals(Double.NaN, (corrupt.parameters["leftEye"] as EffectParameter.Point).x, 0.0)
    }

    @Test fun invalidGeometryAndDuplicateEffectsAreRejected() {
        assertTrue(runCatching {
            PortraitMotionPresets.upsert("clip", listOf(talk),
                PortraitMotionSettings(leftX = 0.45, rightX = 0.46))
        }.isFailure)
        val one = PortraitMotionPresets.upsert("clip", listOf(talk), PortraitMotionSettings())
        assertTrue(runCatching {
            PortraitMotionPresets.upsert("clip", one + one.last(), PortraitMotionSettings())
        }.isFailure)
        assertFalse(PortraitMotionPresets.deserialize(
            PortraitMotionPresets.serialize(PortraitMotionSettings())
        ) == null)
    }
}
