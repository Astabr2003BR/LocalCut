package com.localcut.editor.domain.animation

import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSceneAnimationPlannerTest {
    private fun target(
        id: String,
        durationUs: Long = 5_000_000L,
        x: Double = 0.0,
        y: Double = 0.0,
        scale: Double = 1.0,
    ) = AnimationClipState(
        clipId = id,
        durationUs = durationUs,
        transform = TransformValue(x = x, y = y, scale = scale),
    )

    private fun scalarValues(plan: CharacterAnimationPlan, clipId: String, propertyId: String): List<Double> =
        plan.patches.single { it.clipId == clipId }
            .propertyAnimations.single { it.propertyId == propertyId }
            .keyframes.map { (it.value as PropertyValue.Scalar).value }

    @Test
    fun everyCharacterActionProducesDeterministicFiniteInClipAnimation() {
        val clips = mapOf("body" to target("body"))
        val flatActions = listOf(
            CharacterAction.IDLE,
            CharacterAction.TALK,
            CharacterAction.WALK,
            CharacterAction.CROUCH,
            CharacterAction.STAND,
            CharacterAction.POINT,
            CharacterAction.ENTER,
            CharacterAction.EXIT,
        )

        flatActions.forEach { action ->
            val request = CharacterActionRequest(
                action = action,
                rig = CharacterRigBinding(bodyClipId = "body"),
                startOffsetUs = 500_000L,
                durationUs = 2_000_000L,
                distancePx = 240.0,
            )
            val first = CharacterSceneAnimationPlanner.character(request, clips)
            val second = CharacterSceneAnimationPlanner.character(request, clips)
            assertEquals(first, second)
            assertTrue("$action must produce a body patch", first.patches.any { it.clipId == "body" })
            first.patches.flatMap { it.propertyAnimations }.forEach { animation ->
                assertTrue(animation.validationIssues().isEmpty())
                assertTrue(animation.keyframes.all { it.offsetUs in 0L..5_000_000L })
                animation.keyframes.forEach { keyframe -> assertTrue(keyframe.value.validationIssues().isEmpty()) }
            }
        }
    }

    @Test
    fun walkUsesFlatFallbackAndRiggedCounterMotionWhenPartsExist() {
        val rig = CharacterRigBinding(
            bodyClipId = "body",
            leftArmClipId = "la",
            rightArmClipId = "ra",
            leftLegClipId = "ll",
            rightLegClipId = "rl",
            headClipId = "head",
        )
        val clips = listOf("body", "la", "ra", "ll", "rl", "head")
            .associateWith { target(it, x = 10.0, y = 20.0) }
        val plan = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.WALK,
                rig = rig,
                startOffsetUs = 0L,
                durationUs = 2_000_000L,
                direction = 1.0,
                distancePx = 300.0,
                intensity = 0.8,
            ),
            clips,
        )

        val bodyX = scalarValues(plan, "body", "transform.x")
        assertEquals(10.0, bodyX.first(), 1e-9)
        assertEquals(310.0, bodyX.last(), 1e-9)
        listOf("la", "ra", "ll", "rl").forEach { id ->
            assertTrue(plan.patches.single { it.clipId == id }.propertyAnimations.any { it.propertyId == "transform.rotationDeg" })
        }
    }

    @Test
    fun talkBuildsClosedRestMouthAroundValidatedLipCuesAndBlink() {
        val clips = mapOf(
            "body" to target("body"),
            "mouth" to target("mouth", scale = 1.0),
            "eyes" to target("eyes"),
        )
        val plan = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.TALK,
                rig = CharacterRigBinding(bodyClipId = "body", mouthClipId = "mouth", eyesClipId = "eyes"),
                startOffsetUs = 500_000L,
                durationUs = 2_000_000L,
                lipSyncCues = listOf(
                    LipSyncCue(offsetUs = 100_000L, durationUs = 200_000L, openness = 0.4),
                    LipSyncCue(offsetUs = 600_000L, durationUs = 300_000L, openness = 1.0),
                ),
            ),
            clips,
        )

        val mouthScale = scalarValues(plan, "mouth", "transform.scale")
        assertEquals(1.0, mouthScale.first(), 1e-9)
        assertTrue(mouthScale.max() > 1.0)
        assertEquals(1.0, mouthScale.last(), 1e-9)
        assertTrue(plan.patches.single { it.clipId == "eyes" }.propertyAnimations.any { it.propertyId == "transform.opacity" })

        val overlapping = runCatching {
            CharacterSceneAnimationPlanner.character(
                CharacterActionRequest(
                    action = CharacterAction.TALK,
                    rig = CharacterRigBinding(bodyClipId = "body", mouthClipId = "mouth"),
                    startOffsetUs = 0L,
                    durationUs = 1_000_000L,
                    lipSyncCues = listOf(
                        LipSyncCue(0L, 700_000L, 0.5),
                        LipSyncCue(600_000L, 200_000L, 0.8),
                    ),
                ),
                clips,
            )
        }
        assertTrue(overlapping.isFailure)
    }

    @Test
    fun talkWithoutLipCuesStillUsesLocalMouthOpenCloseFallback() {
        val clips = mapOf(
            "body" to target("body"),
            "mouth" to target("mouth", scale = 1.0),
        )
        val plan = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.TALK,
                rig = CharacterRigBinding(bodyClipId = "body", mouthClipId = "mouth"),
                startOffsetUs = 200_000L,
                durationUs = 1_200_000L,
                intensity = 0.8,
            ),
            clips,
        )

        val mouth = plan.patches.single { it.clipId == "mouth" }
            .propertyAnimations.single { it.propertyId == "transform.scale" }
        val scales = mouth.keyframes.map { (it.value as PropertyValue.Scalar).value }
        assertTrue(mouth.keyframes.size >= 5)
        assertEquals(1.0, scales.first(), 1e-9)
        assertTrue(scales.max() > 1.0)
        assertEquals(1.0, scales.last(), 1e-9)
    }

    @Test
    fun crouchStandPointEnterExitUseExpectedTransformFamilies() {
        val clips = mapOf("body" to target("body", x = 50.0, y = 80.0))
        val rig = CharacterRigBinding(bodyClipId = "body")

        val crouch = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(CharacterAction.CROUCH, rig, 0L, 800_000L), clips,
        )
        assertTrue(scalarValues(crouch, "body", "transform.y").last() > 80.0)

        val stand = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(CharacterAction.STAND, rig, 0L, 800_000L), clips,
        )
        assertTrue(scalarValues(stand, "body", "transform.y").first() > scalarValues(stand, "body", "transform.y").last())

        val point = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(CharacterAction.POINT, rig, 0L, 800_000L), clips,
        )
        assertTrue(point.patches.single().propertyAnimations.any { it.propertyId == "transform.rotationDeg" })

        val enter = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(CharacterAction.ENTER, rig, 0L, 800_000L, direction = 1.0, distancePx = 200.0), clips,
        )
        assertEquals(0.0, scalarValues(enter, "body", "transform.opacity").first(), 1e-9)

        val exit = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(CharacterAction.EXIT, rig, 0L, 800_000L, direction = 1.0, distancePx = 200.0), clips,
        )
        assertEquals(0.0, scalarValues(exit, "body", "transform.opacity").last(), 1e-9)
    }

    @Test
    fun pickupAndPutDownMoveObjectAndRejectMissingTarget() {
        val clips = mapOf(
            "body" to target("body", x = 100.0, y = 100.0),
            "prop" to target("prop", x = 400.0, y = 350.0),
        )
        val rig = CharacterRigBinding(bodyClipId = "body", heldObjectClipId = "prop")
        val pickup = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.PICK_UP,
                rig = rig,
                startOffsetUs = 0L,
                durationUs = 1_500_000L,
                targetX = 130.0,
                targetY = 70.0,
            ),
            clips,
        )
        assertEquals(130.0, scalarValues(pickup, "prop", "transform.x").last(), 1e-9)
        assertEquals(70.0, scalarValues(pickup, "prop", "transform.y").last(), 1e-9)

        val putDown = CharacterSceneAnimationPlanner.character(
            CharacterActionRequest(
                action = CharacterAction.PUT_DOWN,
                rig = rig,
                startOffsetUs = 0L,
                durationUs = 1_500_000L,
                targetX = 500.0,
                targetY = 360.0,
            ),
            clips,
        )
        assertEquals(500.0, scalarValues(putDown, "prop", "transform.x").last(), 1e-9)

        assertTrue(
            runCatching {
                CharacterSceneAnimationPlanner.character(
                    CharacterActionRequest(
                        action = CharacterAction.PICK_UP,
                        rig = CharacterRigBinding(bodyClipId = "body", heldObjectClipId = "missing"),
                        startOffsetUs = 0L,
                        durationUs = 1_000_000L,
                    ),
                    clips,
                )
            }.isFailure,
        )
    }

    @Test
    fun scenePanZoomParallaxAndCrossfadeAreDeterministic() {
        val clips = mapOf(
            "bg" to target("bg", durationUs = 6_000_000L),
            "far" to target("far", durationUs = 6_000_000L),
            "near" to target("near", durationUs = 6_000_000L),
            "next" to target("next", durationUs = 6_000_000L),
        )
        val pan = CharacterSceneAnimationPlanner.scene(
            SceneAnimationRequest(
                kind = SceneAnimationKind.PAN_ZOOM,
                primaryClipId = "bg",
                startOffsetUs = 0L,
                durationUs = 3_000_000L,
                panX = 120.0,
                panY = -40.0,
                zoomTo = 1.12,
            ),
            clips,
        )
        assertEquals(120.0, scalarValues(pan, "bg", "transform.x").last(), 1e-9)
        assertEquals(1.12, scalarValues(pan, "bg", "transform.scale").last(), 1e-9)

        val parallax = CharacterSceneAnimationPlanner.scene(
            SceneAnimationRequest(
                kind = SceneAnimationKind.PARALLAX,
                primaryClipId = "far",
                secondaryClipIds = listOf("near"),
                startOffsetUs = 0L,
                durationUs = 2_000_000L,
                panX = 100.0,
                panY = 20.0,
            ),
            clips,
        )
        val farX = scalarValues(parallax, "far", "transform.x").last()
        val nearX = scalarValues(parallax, "near", "transform.x").last()
        assertTrue(nearX > farX)

        val fade = CharacterSceneAnimationPlanner.scene(
            SceneAnimationRequest(
                kind = SceneAnimationKind.CROSSFADE,
                primaryClipId = "bg",
                incomingClipId = "next",
                startOffsetUs = 1_000_000L,
                durationUs = 1_000_000L,
            ),
            clips,
        )
        assertEquals(0.0, scalarValues(fade, "bg", "transform.opacity").last(), 1e-9)
        assertEquals(0.0, scalarValues(fade, "next", "transform.opacity").first(), 1e-9)
        assertEquals(1.0, scalarValues(fade, "next", "transform.opacity").last(), 1e-9)
    }
}
