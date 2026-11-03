package com.localcut.editor.domain.history

import com.localcut.editor.domain.animation.CharacterAnimationPlan
import com.localcut.editor.domain.animation.ClipAnimationPatch
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
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

class ApplyAnimationPlanCommandTest {
    private fun animation(propertyId: String, from: Double, to: Double): PropertyAnimation = PropertyAnimation(
        propertyId = propertyId,
        keyframes = listOf(
            PropertyKeyframe("$propertyId-a", 0L, PropertyValue.Scalar(from)),
            PropertyKeyframe("$propertyId-b", 1_000_000L, PropertyValue.Scalar(to)),
        ),
    )

    @Test
    fun appliesMultiClipPlanAtomicallyPreservesUnownedPropertiesAndUndoRestores() {
        val existing = animation("effect.customAmount", 0.2, 0.4)
        val initial = project(
            bodyAnimations = listOf(existing),
            propAnimations = emptyList(),
        )
        val plan = CharacterAnimationPlan(
            listOf(
                ClipAnimationPatch("body", listOf(animation("transform.x", 0.0, 120.0))),
                ClipAnimationPatch("prop", listOf(animation("transform.y", 300.0, 100.0))),
            ),
        )
        val history = HistoryManager(initial)

        history.execute(ApplyAnimationPlanCommand(plan))

        val body = history.project.findClip("body")!!
        val prop = history.project.findClip("prop")!!
        assertEquals(setOf("effect.customAmount", "transform.x"), body.propertyAnimations.map { it.propertyId }.toSet())
        assertEquals(listOf("transform.y"), prop.propertyAnimations.map { it.propertyId })
        assertTrue(history.undo())
        assertEquals(initial, history.project)
        assertTrue(history.redo())
        assertEquals(setOf("effect.customAmount", "transform.x"), history.project.findClip("body")!!.propertyAnimations.map { it.propertyId }.toSet())
    }

    @Test
    fun replacesOnlyOwnedPropertyInsteadOfDuplicatingIt() {
        val oldX = animation("transform.x", 0.0, 10.0)
        val keepOpacity = animation("transform.opacity", 1.0, 0.8)
        val initial = project(bodyAnimations = listOf(oldX, keepOpacity), propAnimations = emptyList())
        val replacementX = animation("transform.x", 10.0, 200.0)

        val result = ApplyAnimationPlanCommand(
            CharacterAnimationPlan(listOf(ClipAnimationPatch("body", listOf(replacementX)))),
        ).apply(initial)

        val animations = result.project.findClip("body")!!.propertyAnimations
        assertEquals(2, animations.size)
        assertEquals(1, animations.count { it.propertyId == "transform.x" })
        assertEquals(replacementX, animations.single { it.propertyId == "transform.x" })
        assertEquals(keepOpacity, animations.single { it.propertyId == "transform.opacity" })
    }

    @Test
    fun missingClipFailsBeforeAnyMutation() {
        val initial = project()
        val plan = CharacterAnimationPlan(
            listOf(
                ClipAnimationPatch("body", listOf(animation("transform.x", 0.0, 100.0))),
                ClipAnimationPatch("missing", listOf(animation("transform.y", 0.0, 100.0))),
            ),
        )

        val error = assertThrows(TimelineEditException::class.java) {
            ApplyAnimationPlanCommand(plan).apply(initial)
        }
        assertEquals(TimelineErrorCode.CLIP_NOT_FOUND, error.code)
        assertTrue(initial.findClip("body")!!.propertyAnimations.isEmpty())
    }

    @Test
    fun lockedAffectedTrackFailsClosed() {
        val initial = project(lockedBodyTrack = true)
        val plan = CharacterAnimationPlan(
            listOf(ClipAnimationPatch("body", listOf(animation("transform.x", 0.0, 100.0)))),
        )

        val error = assertThrows(TimelineEditException::class.java) {
            ApplyAnimationPlanCommand(plan).apply(initial)
        }
        assertEquals(TimelineErrorCode.TRACK_LOCKED, error.code)
    }

    private fun project(
        bodyAnimations: List<PropertyAnimation> = emptyList(),
        propAnimations: List<PropertyAnimation> = emptyList(),
        lockedBodyTrack: Boolean = false,
    ): Project {
        val asset = Asset(
            id = "asset",
            type = AssetType.IMAGE,
            uri = "content://animation",
            sourceDurationUs = 5_000_000L,
            sizeBytes = 1L,
            displayName = "character.png",
            mimeType = "image/png",
            fingerprint = "fp",
        )
        val body = Clip(
            id = "body",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L,
            propertyAnimations = bodyAnimations,
        )
        val prop = Clip(
            id = "prop",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L,
            propertyAnimations = propAnimations,
        )
        return Project.create("Animation", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "body-track",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.OVERLAY,
                    locked = lockedBodyTrack,
                    clips = listOf(body),
                ),
                Track(
                    id = "prop-track",
                    type = TrackType.VISUAL,
                    order = 1,
                    collisionPolicy = TrackCollisionPolicy.OVERLAY,
                    clips = listOf(prop),
                ),
                Track("audio", TrackType.AUDIO, 2),
            ),
        )
    }
}
