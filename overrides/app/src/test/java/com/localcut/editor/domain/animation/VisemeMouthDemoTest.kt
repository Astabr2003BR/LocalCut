package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisemeMouthDemoTest {
    private fun create(existing: List<EffectInstance> = emptyList(), durationUs: Long = 5_000_000L) =
        VisemeMouthDemo.apply("face", durationUs, existing, 0.51, 0.49, 0.07, 0.025)

    @Test fun demoGeneratesAnimatedVisemesAndReturnsToRest() {
        val effect = create().single()
        assertEquals(4, effect.animations.size)
        assertEquals(setOf("openness", "widthScale", "rounding", "teethVisibility"),
            effect.animations.map { it.propertyId }.toSet())
        assertTrue(EffectRegistry.validationIssues(effect).isEmpty())
        val a = EffectRegistry.evaluate(effect, 600_000L)
        val o = EffectRegistry.evaluate(effect, 1_400_000L)
        fun number(e: EffectInstance, name: String) =
            (e.parameters.getValue(name) as EffectParameter.Number).value
        assertTrue(number(a, "openness") > number(o, "openness"))
        assertTrue(number(o, "rounding") > number(a, "rounding"))
        assertTrue(number(o, "widthScale") < number(a, "widthScale"))
        assertEquals(0.0, number(EffectRegistry.evaluate(effect, 3_700_000L), "openness"), 1e-9)
        assertEquals(0.0, number(EffectRegistry.evaluate(effect, 0L), "openness"), 1e-9)
        effect.animations.forEach { anim ->
            assertTrue(anim.keyframes.size >= 20)
            assertTrue(anim.keyframes.zipWithNext().all { (a, b) -> a.offsetUs < b.offsetUs })
            assertTrue(anim.keyframes.last().offsetUs <= 5_000_000L)
        }
    }

    @Test fun preservesBlinkAndRemovesOnlyOwnedDemo() {
        val blink = EffectInstance("blink", "localcut.character.eye_blink")
        val added = create(listOf(blink))
        assertEquals(blink, added.first())
        assertEquals(2, added.size)
        assertEquals(listOf(blink), VisemeMouthDemo.remove(added))
    }

    @Test fun refusesExistingTalkAndIdCollisions() {
        assertTrue(runCatching { create(listOf(EffectInstance("talk", VisemeMouthDemo.TYPE_ID))) }.isFailure)
        val colliding = EffectInstance(VisemeMouthDemo.ID_PREFIX + "face", "other.type")
        assertTrue(runCatching { create(listOf(colliding)) }.isFailure)
    }

    @Test fun rejectsInvalidGeometryAndDuration() {
        assertTrue(runCatching { create(durationUs = 100_000L) }.isFailure)
        assertTrue(runCatching { VisemeMouthDemo.apply("face", 5_000_000L, emptyList(), Double.NaN, 0.49, 0.07, 0.025) }.isFailure)
        assertTrue(runCatching { VisemeMouthDemo.apply("face", 5_000_000L, emptyList(), 0.02, 0.49, 0.07, 0.025) }.isFailure)
        assertTrue(runCatching { VisemeMouthDemo.apply("", 5_000_000L, emptyList(), 0.51, 0.49, 0.07, 0.025) }.isFailure)
    }

    @Test fun longAndShortClipsHaveBoundedKeyframes() {
        for (durationUs in listOf(VisemeMouthDemo.MIN_DURATION_US, 10_000_000L, 600_000_000L)) {
            val effect = create(durationUs = durationUs).single()
            assertTrue(EffectRegistry.validationIssues(effect).isEmpty())
            assertTrue(effect.animations.all { it.keyframes.last().offsetUs <= durationUs })
            assertTrue(effect.animations.all { it.keyframes.last().offsetUs <= 4_000_000L })
        }
    }
}
