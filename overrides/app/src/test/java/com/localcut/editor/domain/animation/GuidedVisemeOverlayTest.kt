package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.effects.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedVisemeOverlayTest {
    private fun talk(): EffectInstance = EffectInstance(
        id = "existing-talk",
        typeId = "localcut.character.mouth_warp",
        parameters = mapOf(
            "center" to EffectParameter.Point(0.5, 0.65),
            "radiusX" to EffectParameter.Number(0.07),
            "radiusY" to EffectParameter.Number(0.025),
            "openness" to EffectParameter.Number(0.0),
        ),
        animations = listOf(
            PropertyAnimation(
                "openness",
                listOf(
                    PropertyKeyframe("talk0", 0L, PropertyValue.Scalar(0.0)),
                    PropertyKeyframe("talk1", 600_000L, PropertyValue.Scalar(0.70)),
                    PropertyKeyframe("talk2", 1_500_000L, PropertyValue.Scalar(0.5)),
                    PropertyKeyframe("talk3", 2_500_000L, PropertyValue.Scalar(0.7)),
                    PropertyKeyframe("talk4", 3_500_000L, PropertyValue.Scalar(0.0)),
                    PropertyKeyframe("talk5", 5_000_000L, PropertyValue.Scalar(0.0)),
                ),
            ),
        ),
    )

    @Test fun portugueseTextCreatesBoundedPhoneticApproximation() {
        val cues = PortugueseVisemeGuide.plan("Oi! Agora minha voz funciona.", 4_000_000L)
        assertTrue(cues.size > 5)
        assertTrue(cues.any { it.shape == VisemeShape.A })
        assertTrue(cues.any { it.shape == VisemeShape.O })
        assertTrue(cues.any { it.shape == VisemeShape.M_B_P })
        assertTrue(cues.any { it.shape == VisemeShape.REST })
        assertEquals(0L, cues.first().offsetUs)
        assertEquals(4_000_000L, cues.last().endUs)
        assertTrue(cues.zipWithNext().all { (a, b) -> a.endUs == b.offsetUs })
        assertEquals(cues, PortugueseVisemeGuide.plan("Oi! Agora minha voz funciona.", 4_000_000L))
    }

    @Test fun guidedOverlayPreservesOriginalTalkAndOtherEffects() {
        val prior = talk()
        val blink = EffectInstance(id = "blink", typeId = "localcut.character.eye_blink")
        val source = listOf(prior, blink)
        val guided = GuidedVisemeOverlay.apply(5_000_000L, source, "Oi, agora eu falo!")
        assertEquals(2, guided.size)
        assertEquals(blink, guided.last())
        assertEquals(prior.parameters, guided.first().parameters)
        assertEquals(prior.animations.single(), guided.first().animations.first())
        assertEquals(4, guided.first().animations.size)
        assertTrue(GuidedVisemeOverlay.hasGuidance(guided.first()))
        assertTrue(EffectRegistry.validationIssues(guided.first()).isEmpty())
        assertEquals(source, GuidedVisemeOverlay.remove(guided))
    }

    @Test fun repeatedApplyIsIdempotent() {
        val once = GuidedVisemeOverlay.apply(5_000_000L, listOf(talk()), "Oi agora")
        val twice = GuidedVisemeOverlay.apply(5_000_000L, once, "Oi agora")
        assertEquals(once, twice)
        val different = GuidedVisemeOverlay.apply(5_000_000L, once, "Oi vida")
        assertEquals(4, different.single().animations.size)
        assertTrue(different != once)
    }

    @Test fun guidedVisemesVaryShapeButDoNotChangeWaveform() {
        val original = talk()
        val result = GuidedVisemeOverlay.apply(5_000_000L, listOf(original), "O A F U M E")
            .single()
        val atO = EffectRegistry.evaluate(result, 700_000L)
        val atA = EffectRegistry.evaluate(result, 1_100_000L)
        fun value(effect: EffectInstance, field: String): Double =
            (effect.parameters.getValue(field) as EffectParameter.Number).value
        assertTrue(value(atO, "rounding") >= 0.0)
        assertTrue(value(atA, "widthScale") in 0.60..1.30)
        for (time in listOf(0L, 600_000L, 1_000_000L, 2_500_000L, 4_500_000L)) {
            assertEquals(value(EffectRegistry.evaluate(original, time), "openness"),
                value(EffectRegistry.evaluate(result, time), "openness"), 1e-9)
        }
        assertEquals(0.0, value(EffectRegistry.evaluate(result, 5_000_000L), "rounding"), 1e-9)
    }

    @Test fun malformedSpeechAndProjectsFailWithoutDataChanges() {
        val original = listOf(talk())
        assertTrue(runCatching { GuidedVisemeOverlay.apply(5_000_000L, original, "  !   ") }.isFailure)
        assertTrue(runCatching { GuidedVisemeOverlay.apply(5_000_000L, original, "a".repeat(161)) }.isFailure)
        assertTrue(runCatching { GuidedVisemeOverlay.apply(0L, original, "ola") }.isFailure)
        assertTrue(runCatching { GuidedVisemeOverlay.apply(5_000_000L, emptyList(), "ola") }.isFailure)
        assertTrue(runCatching { GuidedVisemeOverlay.apply(5_000_000L, original + talk(), "ola") }.isFailure)
        assertTrue(runCatching { GuidedVisemeOverlay.apply(5_000_000L,
            listOf(talk().copy(enabled = false)), "ola") }.isFailure)
        assertFalse(GuidedVisemeOverlay.hasGuidance(original.single()))
        assertEquals(original, GuidedVisemeOverlay.remove(original))
    }

    @Test fun incompatibleCustomShapeTracksArePreservedAndBlockOverwrite() {
        val custom = talk().copy(animations = talk().animations + PropertyAnimation(
            "rounding", listOf(PropertyKeyframe("custom", 0L, PropertyValue.Scalar(0.5))),
        ))
        assertTrue(runCatching {
            GuidedVisemeOverlay.apply(5_000_000L, listOf(custom), "Olá")
        }.isFailure)
        assertEquals(listOf(custom), GuidedVisemeOverlay.remove(listOf(custom)))
    }

    @Test fun PortugueseGuideDoesNotAcceptDegenerateOrHugeAudioDurations() {
        assertTrue(runCatching { PortugueseVisemeGuide.plan("Ola", 10L) }.isFailure)
        assertTrue(runCatching { PortugueseVisemeGuide.plan("Ola", Long.MAX_VALUE) }.isFailure)
        assertTrue(runCatching { PortugueseVisemeGuide.plan("", 5_000_000L) }.isFailure)
        assertTrue(runCatching { PortugueseVisemeGuide.plan("🙂🙂", 5_000_000L) }.isFailure)
        assertTrue(PortugueseVisemeGuide.plan("A ação é séria?", 2_000_000L).isNotEmpty())
    }
}
