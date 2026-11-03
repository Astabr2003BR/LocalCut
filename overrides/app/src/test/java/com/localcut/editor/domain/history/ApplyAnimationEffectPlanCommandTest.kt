package com.localcut.editor.domain.history

import com.localcut.editor.domain.animation.CharacterAnimationPlan
import com.localcut.editor.domain.animation.ClipEffectPatch
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Easing
import com.localcut.editor.domain.model.EasingKind
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineEditException
import com.localcut.editor.domain.timeline.TimelineErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyAnimationEffectPlanCommandTest {
    private val unrelated = EffectInstance(
        id = "existing-vignette",
        typeId = "localcut.vignette",
        parameters = mapOf("amount" to EffectParameter.Number(0.25)),
    )

    private fun mouthEffect(openAtUs: Long = 400_000L): EffectInstance = EffectInstance(
        id = "localcut-character-mouth-warp",
        typeId = "localcut.character.mouth_warp",
        parameters = mapOf(
            "center" to EffectParameter.Point(0.5, 0.66),
            "radiusX" to EffectParameter.Number(0.1),
            "radiusY" to EffectParameter.Number(0.05),
            "openness" to EffectParameter.Number(0.0),
        ),
        animations = listOf(
            PropertyAnimation(
                propertyId = "openness",
                keyframes = listOf(
                    PropertyKeyframe("mouth-0", 0L, PropertyValue.Scalar(0.0), Easing(EasingKind.EASE_IN_OUT)),
                    PropertyKeyframe("mouth-open", openAtUs, PropertyValue.Scalar(0.8), Easing(EasingKind.EASE_IN_OUT)),
                    PropertyKeyframe("mouth-end", 800_000L, PropertyValue.Scalar(0.0)),
                ),
            ),
        ),
    )

    @Test
    fun effectOnlyAnimationPatchPreservesUnrelatedEffectsAndUndoRedoRestoresExactly() {
        val initial = project(effects = listOf(unrelated))
        val plan = CharacterAnimationPlan(
            patches = emptyList(),
            effectPatches = listOf(ClipEffectPatch("body", listOf(mouthEffect()))),
        )
        val history = HistoryManager(initial)

        history.execute(ApplyAnimationPlanCommand(plan))

        val after = history.project.findClip("body")!!
        assertEquals(listOf("existing-vignette", "localcut-character-mouth-warp"), after.effects.map { it.id })
        assertEquals(1, after.effects.count { it.typeId == "localcut.character.mouth_warp" })
        assertTrue(history.undo())
        assertEquals(initial, history.project)
        assertTrue(history.redo())
        assertEquals(after.effects, history.project.findClip("body")!!.effects)
    }

    @Test
    fun repeatedMouthPatchUpsertsOwnedEffectAndPreservesOldAnimationOutsideNewWindow() {
        val first = mouthEffect(openAtUs = 300_000L).copy(
            animations = listOf(
                PropertyAnimation(
                    propertyId = "openness",
                    keyframes = listOf(
                        PropertyKeyframe("old-0", 0L, PropertyValue.Scalar(0.0)),
                        PropertyKeyframe("old-300", 300_000L, PropertyValue.Scalar(0.7)),
                        PropertyKeyframe("old-1500", 1_500_000L, PropertyValue.Scalar(0.4)),
                        PropertyKeyframe("old-1800", 1_800_000L, PropertyValue.Scalar(0.0)),
                    ),
                ),
            ),
        )
        val replacement = mouthEffect(openAtUs = 700_000L).copy(
            animations = listOf(
                PropertyAnimation(
                    propertyId = "openness",
                    keyframes = listOf(
                        PropertyKeyframe("new-500", 500_000L, PropertyValue.Scalar(0.0)),
                        PropertyKeyframe("new-700", 700_000L, PropertyValue.Scalar(1.0)),
                        PropertyKeyframe("new-1000", 1_000_000L, PropertyValue.Scalar(0.0)),
                    ),
                ),
            ),
        )
        val initial = project(effects = listOf(unrelated, first))

        val result = ApplyAnimationPlanCommand(
            CharacterAnimationPlan(
                patches = emptyList(),
                effectPatches = listOf(ClipEffectPatch("body", listOf(replacement))),
            ),
        ).apply(initial)

        val effects = result.project.findClip("body")!!.effects
        assertEquals(1, effects.count { it.typeId == "localcut.character.mouth_warp" })
        val openness = effects.single { it.typeId == "localcut.character.mouth_warp" }.animations.single()
        assertEquals(
            listOf(0L, 300_000L, 500_000L, 700_000L, 1_000_000L, 1_500_000L, 1_800_000L),
            openness.keyframes.map { it.offsetUs },
        )
    }

    @Test
    fun effectOnlyPatchOnLockedTrackFailsBeforeMutation() {
        val initial = project(effects = listOf(unrelated), locked = true)
        val command = ApplyAnimationPlanCommand(
            CharacterAnimationPlan(
                patches = emptyList(),
                effectPatches = listOf(ClipEffectPatch("body", listOf(mouthEffect()))),
            ),
        )

        val error = assertThrows(TimelineEditException::class.java) { command.apply(initial) }
        assertEquals(TimelineErrorCode.TRACK_LOCKED, error.code)
        assertEquals(listOf(unrelated), initial.findClip("body")!!.effects)
    }

    private fun project(effects: List<EffectInstance>, locked: Boolean = false): Project {
        val asset = Asset(
            id = "asset",
            type = AssetType.IMAGE,
            uri = "content://flat-character",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 1L,
            displayName = "character.png",
            mimeType = "image/png",
            fingerprint = "flat-character-fp",
        )
        val body = Clip(
            id = "body",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
            effects = effects,
        )
        return Project.create("Flat character", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual",
                    type = TrackType.VISUAL,
                    order = 0,
                    locked = locked,
                    collisionPolicy = TrackCollisionPolicy.OVERLAY,
                    clips = listOf(body),
                ),
                Track("audio", TrackType.AUDIO, 1),
            ),
        )
    }
}
