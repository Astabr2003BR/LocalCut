package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterFlatMouthPlannerTest {
    private val body = AnimationClipState(
        clipId = "body",
        durationUs = 5_000_000L,
        transform = TransformValue(),
    )

    @Test
    fun flatTalkWithMouthRegionProducesLocalizedMouthEffectWithFallbackAnimation() {
        val plan = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.TALK,
                rig = CharacterRigBinding(bodyClipId = "body"),
                startOffsetUs = 200_000L,
                durationUs = 1_200_000L,
                intensity = 0.8,
                mouthRegion = MouthRegion(
                    centerX = 0.50,
                    centerY = 0.66,
                    radiusX = 0.10,
                    radiusY = 0.045,
                ),
            ),
            mapOf("body" to body),
        )

        val patch = plan.effectPatches.single()
        assertEquals("body", patch.clipId)
        val effect = patch.effects.single()
        assertEquals("localcut.character.mouth_warp", effect.typeId)
        assertEquals(EffectParameter.Point(0.50, 0.66), effect.parameters["center"])
        assertEquals(EffectParameter.Number(0.10), effect.parameters["radiusX"])
        assertEquals(EffectParameter.Number(0.045), effect.parameters["radiusY"])
        val openness = effect.animations.single { it.propertyId == "openness" }
        val values = openness.keyframes.map { (it.value as PropertyValue.Scalar).value }
        assertTrue(openness.keyframes.size >= 5)
        assertEquals(0.0, values.first(), 1e-9)
        assertTrue(values.max() > 0.0)
        assertEquals(0.0, values.last(), 1e-9)
    }

    @Test
    fun explicitMouthClipKeepsRiggedPathAndDoesNotAddFlatMouthEffect() {
        val mouth = body.copy(clipId = "mouth")
        val plan = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.TALK,
                rig = CharacterRigBinding(bodyClipId = "body", mouthClipId = "mouth"),
                startOffsetUs = 0L,
                durationUs = 1_000_000L,
                mouthRegion = MouthRegion(0.5, 0.65, 0.1, 0.05),
            ),
            mapOf("body" to body, "mouth" to mouth),
        )

        assertTrue(plan.effectPatches.isEmpty())
        assertTrue(
            plan.patches.single { it.clipId == "mouth" }
                .propertyAnimations.any { it.propertyId == "transform.scale" },
        )
    }

    @Test
    fun flatMouthRegionRejectsInvalidNormalizedBounds() {
        val invalid = listOf(
            MouthRegion(-0.1, 0.6, 0.1, 0.05),
            MouthRegion(0.5, 1.1, 0.1, 0.05),
            MouthRegion(0.5, 0.6, 0.0, 0.05),
            MouthRegion(0.5, 0.6, 0.6, 0.05),
            MouthRegion(0.5, 0.6, 0.1, 0.0),
            MouthRegion(0.5, 0.6, 0.1, 0.31),
        )

        invalid.forEach { region ->
            assertTrue(
                runCatching {
                    CharacterSceneAnimationPlanner.character(
                        CharacterActionRequest(
                            action = CharacterAction.TALK,
                            rig = CharacterRigBinding(bodyClipId = "body"),
                            startOffsetUs = 0L,
                            durationUs = 500_000L,
                            mouthRegion = region,
                        ),
                        mapOf("body" to body),
                    )
                }.isFailure,
            )
        }
    }

    @Test
    fun flatMouthUsesProvidedLipCuesInsteadOfRhythmicFallback() {
        val plan = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.TALK,
                rig = CharacterRigBinding(bodyClipId = "body"),
                startOffsetUs = 100_000L,
                durationUs = 1_000_000L,
                mouthRegion = MouthRegion(0.5, 0.65, 0.1, 0.05),
                lipSyncCues = listOf(
                    LipSyncCue(100_000L, 150_000L, 0.25),
                    LipSyncCue(500_000L, 200_000L, 0.9),
                ),
            ),
            mapOf("body" to body),
        )

        val animation = plan.effectPatches.single().effects.single().animations.single()
        val values = animation.keyframes.map { (it.value as PropertyValue.Scalar).value }
        assertTrue(values.any { kotlin.math.abs(it - 0.25) < 1e-9 })
        assertTrue(values.any { kotlin.math.abs(it - 0.9) < 1e-9 })
    }
}
