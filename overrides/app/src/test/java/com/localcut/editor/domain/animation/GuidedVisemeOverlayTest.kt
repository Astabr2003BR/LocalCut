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
        assertEquals(5, guided.first().animations.size)
        assertTrue(GuidedVisemeOverlay.hasGuidance(guided.first()))
        assertTrue(EffectRegistry.validationIssues(guided.first()).isEmpty())
        assertEquals(source, GuidedVisemeOverlay.remove(guided))
    }

    @Test fun guidedBilabialClosureHasSeparateGateAndPreservesWaveform() {
        val base = talk()
        val guided = GuidedVisemeOverlay.apply(5_000_000L, listOf(base), "ma").single()
        val gate = guided.animations.single { it.propertyId == "articulationGate" }
        val values = gate.keyframes.map { (it.value as PropertyValue.Scalar).value }
        assertTrue(values.all { it in 0.0..1.0 })
        assertTrue(values.any { it == 0.0 })
        assertTrue(values.any { it > 0.5 })
        assertEquals(base.animations.single(), guided.animations.first())
        assertEquals(listOf(base), GuidedVisemeOverlay.remove(listOf(guided)))
    }

    @Test fun repeatedApplyIsIdempotent() {
        val once = GuidedVisemeOverlay.apply(5_000_000L, listOf(talk()), "Oi agora")
        val twice = GuidedVisemeOverlay.apply(5_000_000L, once, "Oi agora")
        assertEquals(once, twice)
        val different = GuidedVisemeOverlay.apply(5_000_000L, once, "Oi vida")
        assertEquals(5, different.single().animations.size)
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

    @Test fun guidedSpeechRespectsLongSilenceBetweenTwoTalkingBursts() {
        val base = talk()
        val twoBursts = base.copy(animations = listOf(PropertyAnimation(
            "openness", listOf(
                PropertyKeyframe("s0", 0L, PropertyValue.Scalar(0.0)),
                PropertyKeyframe("s1", 250_000L, PropertyValue.Scalar(0.8)),
                PropertyKeyframe("s2", 950_000L, PropertyValue.Scalar(0.8)),
                PropertyKeyframe("s3", 1_100_000L, PropertyValue.Scalar(0.0)),
                PropertyKeyframe("s4", 2_300_000L, PropertyValue.Scalar(0.0)),
                PropertyKeyframe("s5", 2_500_000L, PropertyValue.Scalar(0.7)),
                PropertyKeyframe("s6", 3_300_000L, PropertyValue.Scalar(0.7)),
                PropertyKeyframe("s7", 3_500_000L, PropertyValue.Scalar(0.0)),
                PropertyKeyframe("s8", 5_000_000L, PropertyValue.Scalar(0.0)),
            ),
        )))
        val windows = TalkSpeechWindows.detect(twoBursts.animations.single(), 5_000_000L)
        assertEquals(listOf(
            TalkSpeechWindow(0L, 1_100_000L),
            TalkSpeechWindow(2_300_000L, 3_500_000L),
        ), windows)
        val source = listOf(twoBursts)
        val guided = GuidedVisemeOverlay.apply(5_000_000L, source, "O A U M E")
        assertEquals(source, GuidedVisemeOverlay.remove(guided))
        val fx = guided.single()
        val quiet = EffectRegistry.evaluate(fx, 1_700_000L)
        assertEquals(1.0,
            (quiet.parameters.getValue("widthScale") as EffectParameter.Number).value, 1e-9)
        assertEquals(0.0,
            (quiet.parameters.getValue("rounding") as EffectParameter.Number).value, 1e-9)
        assertEquals(0.0,
            (quiet.parameters.getValue("teethVisibility") as EffectParameter.Number).value, 1e-9)
        assertEquals(4, fx.animations.size - twoBursts.animations.size)
        assertTrue(EffectRegistry.validationIssues(fx).isEmpty())
        for (at in listOf(0L, 950_000L, 1_700_000L, 2_500_000L, 4_500_000L)) {
            val before = EffectRegistry.evaluate(twoBursts, at)
            val after = EffectRegistry.evaluate(fx, at)
            assertEquals(before.parameters.getValue("openness"),
                after.parameters.getValue("openness"))
        }
    }

    @Test fun speechWindowMappingSplitsCueAtPauseAndBoundsItsDuration() {
        val windows = listOf(TalkSpeechWindow(0L, 800_000L),
            TalkSpeechWindow(1_500_000L, 2_100_000L))
        val cues = listOf(VisemeCue(0L, 1_400_000L, VisemeShape.O))
        val output = TalkSpeechWindows.distribute(cues, windows)
        assertEquals(listOf(
            VisemeCue(0L, 800_000L, VisemeShape.O),
            VisemeCue(1_500_000L, 600_000L, VisemeShape.O),
        ), output)
        val timeline = VisemeTimeline(output, 2_200_000L)
        assertEquals(VisemePose.REST, timeline.poseAt(1_100_000L))
        assertEquals(VisemePose.REST, timeline.poseAt(2_100_000L))
    }

    @Test fun tooManySpeechWindowsAreRejectedWithoutMutation() {
        val keys = mutableListOf(PropertyKeyframe("begin", 0L, PropertyValue.Scalar(0.0)))
        for (index in 0 until 34) {
            val base = 250_000L * index
            keys += PropertyKeyframe("peak" + index, base + 60_000L,
                PropertyValue.Scalar(0.8))
            keys += PropertyKeyframe("stop" + index, base + 80_000L,
                PropertyValue.Scalar(0.0))
            keys += PropertyKeyframe("pause" + index, base + 200_000L,
                PropertyValue.Scalar(0.0))
        }
        assertTrue(runCatching {
            TalkSpeechWindows.detect(PropertyAnimation("openness", keys), 9_000_000L)
        }.isFailure)
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
