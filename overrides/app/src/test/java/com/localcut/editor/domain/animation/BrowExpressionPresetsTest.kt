package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.effects.EffectRegistry
import com.localcut.editor.media.LocalCutBrowExpressionEffect
import com.localcut.editor.media.BrowExpressionMath
import com.localcut.editor.media.Media3EffectStackFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowExpressionPresetsTest {
    private val talk = EffectInstance(id = "talk", typeId = "localcut.character.mouth_warp")
    private val blink = EffectInstance(id = "blink", typeId = "localcut.character.eye_blink")

    private fun create(emotion: BrowEmotion = BrowEmotion.SURPRISED) =
        BrowExpressionSettings(
            leftX = 0.38, rightX = 0.62, browY = 0.28,
            radiusX = 0.07, radiusY = 0.04,
            emotion = emotion, intensity = 0.8,
        )

    @Test fun expressionPresetsRenderAndPreserveOtherEffects() {
        val original = listOf(talk, blink)
        val created = BrowExpressionPresets.upsert("portrait", original, create())
        assertEquals(3, created.size)
        assertEquals(talk, created[0])
        assertEquals(blink, created[1])
        assertEquals(BrowExpressionPresets.TYPE_ID, created.last().typeId)
        assertTrue(EffectRegistry.validationIssues(created.last()).isEmpty())
        assertTrue(Media3EffectStackFactory.canRender(created.last()))
        assertFalse(LocalCutBrowExpressionEffect(created.last()).isNoOp(320, 180))
        assertEquals(create(), BrowExpressionPresets.fromEffectOrNull(created.last()))
        assertEquals(original, BrowExpressionPresets.remove(created))
        assertEquals(original, BrowExpressionPresets.upsert(
            "portrait", created, create().copy(emotion = BrowEmotion.NEUTRAL)))
    }

    @Test fun upsertIsIdempotentAndReplacesOnlyItsOwnInstance() {
        val source = listOf(talk, blink)
        val once = BrowExpressionPresets.upsert("portrait", source, create())
        val twice = BrowExpressionPresets.upsert("portrait", once, create())
        assertEquals(once, twice)
        val worried = BrowExpressionPresets.upsert(
            "portrait", once, create(BrowEmotion.WORRIED))
        assertEquals(once.last().id, worried.last().id)
        assertEquals(once.size, worried.size)
        assertEquals(source, BrowExpressionPresets.remove(worried))
        assertEquals(source, BrowExpressionPresets.upsert(
            "portrait", worried, create().copy(intensity = 0.0)))
    }

    @Test fun invalidGeometryAndCollisionsAreRejected() {
        val bad = listOf(
            create().copy(leftX = Double.NaN),
            create().copy(browY = Double.POSITIVE_INFINITY),
            create().copy(radiusX = 0.0),
            create().copy(radiusY = 0.20),
            create().copy(rightX = 0.39),
            create().copy(intensity = -0.1),
            create().copy(intensity = 1.1),
        )
        bad.forEach { settings ->
            assertTrue(runCatching {
                BrowExpressionPresets.upsert("portrait", listOf(talk), settings)
            }.isFailure)
        }
        assertTrue(runCatching {
            BrowExpressionPresets.upsert(" ", listOf(talk), create())
        }.isFailure)
        val one = BrowExpressionPresets.upsert("portrait", listOf(talk), create())
        assertTrue(runCatching {
            BrowExpressionPresets.upsert("portrait", one + one.last(), create())
        }.isFailure)
        val collide = EffectInstance("brow-expression-portrait", "other.type")
        assertTrue(runCatching {
            BrowExpressionPresets.upsert("portrait", listOf(talk, collide), create())
        }.isFailure)
        assertEquals(listOf(talk, collide), BrowExpressionPresets.remove(listOf(talk, collide)))
    }

    @Test fun unknownOrFractionalEmotionCannotBeRendered() {
        assertTrue(runCatching { BrowEmotion.fromCode(1.5) }.isFailure)
        assertTrue(runCatching { BrowEmotion.fromCode(Double.NaN) }.isFailure)
        val effect = BrowExpressionPresets.upsert("portrait", emptyList(), create()).single()
        val corrupt = effect.copy(parameters = effect.parameters +
            ("emotion" to EffectParameter.Number(2.4)))
        assertTrue(EffectRegistry.validationIssues(corrupt).isNotEmpty())
        assertFalse(Media3EffectStackFactory.canRender(corrupt))
        assertNull(BrowExpressionPresets.fromEffectOrNull(corrupt))
        val overlap = effect.copy(parameters = effect.parameters +
            ("rightBrow" to EffectParameter.Point(0.41, 0.28)))
        assertFalse(Media3EffectStackFactory.canRender(overlap))
        val keyed = effect.copy(animations = listOf(
            com.localcut.editor.domain.model.PropertyAnimation(
                "intensity",
                listOf(com.localcut.editor.domain.model.PropertyKeyframe(
                    "invalid", 0L,
                    com.localcut.editor.domain.model.PropertyValue.Scalar(0.2),
                )),
            ),
        ))
        assertFalse(Media3EffectStackFactory.canRender(keyed))
    }

    @Test fun browMathIsBoundedDirectionalAndDeterministic() {
        for (type in BrowEmotion.entries) {
            assertEquals(0.0, BrowExpressionMath.shiftY(type, 0.0, 0.0, true), 0.0)
            for (x in listOf(-1.0, -0.4, 0.0, 0.4, 1.0)) {
                val result = BrowExpressionMath.shiftY(type, 0.8, x, true)
                assertTrue(result.isFinite() && result in -0.022..0.022)
                assertEquals(result, BrowExpressionMath.shiftY(type, 0.8, x, true), 0.0)
            }
        }
        assertTrue(BrowExpressionMath.shiftY(BrowEmotion.SURPRISED, 1.0, 0.0, true) > 0.0)
        assertTrue(BrowExpressionMath.shiftY(BrowEmotion.WORRIED, 1.0, 1.0, true) > 0.0)
        assertTrue(BrowExpressionMath.shiftY(BrowEmotion.WORRIED, 1.0, -1.0, true) < 0.0)
        assertTrue(BrowExpressionMath.shiftY(BrowEmotion.DETERMINED, 1.0, 1.0, true) < 0.0)
        assertTrue(BrowExpressionMath.shiftY(BrowEmotion.WORRIED, 1.0, -1.0, false) > 0.0)
        assertTrue(runCatching {
            BrowExpressionMath.shiftY(BrowEmotion.SURPRISED, Double.NaN, 0.0, true)
        }.isFailure)
        assertTrue(runCatching {
            BrowExpressionMath.shiftY(BrowEmotion.SURPRISED, 1.0, Double.NaN, true)
        }.isFailure)
    }
}
