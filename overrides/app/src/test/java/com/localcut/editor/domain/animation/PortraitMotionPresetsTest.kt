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
