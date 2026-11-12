package com.localcut.editor.domain.animation

import com.localcut.editor.domain.history.ApplyAnimationPlanCommand
import com.localcut.editor.domain.history.HistoryManager
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeredCharacterActionPolicyTest {
    private fun project(locked: Boolean = false, shifted: Boolean = false): Project {
        val ids = listOf("body", "head", "la", "ra", "ll", "rl")
        val assets = ids.map { Asset(id = it, type = AssetType.IMAGE, uri = "content://layer/" + it,
            sourceDurationUs = 5_000_000L, sizeBytes = 1L, displayName = it + ".png",
            mimeType = "image/png", fingerprint = "fp-" + it) }
        val tracks = ids.mapIndexed { index, id ->
            Track(id = "track-" + id, type = TrackType.VISUAL, order = index,
                collisionPolicy = TrackCollisionPolicy.OVERLAY, locked = locked && id == "ra",
                clips = listOf(Clip(id, id, if (shifted && id == "ra") 100_000L else 0L,
                    5_000_000L, 0L, 5_000_000L)))
        }
        return Project.create("Rig", 1080, 1920, 30).copy(assets = assets, tracks = tracks)
    }
    private val rig = CharacterRigBinding("body", "head", leftArmClipId = "la",
        rightArmClipId = "ra", leftLegClipId = "ll", rightLegClipId = "rl")

    @Test fun stationaryWalkIsAtomicAndUndoRestoresAllLayers() {
        val original = project()
        val plan = LayeredCharacterActionPolicy.plan(original, rig, CharacterAction.WALK, 2_000_000L, 0.7)
        assertEquals(6, plan.patches.size)
        val x = plan.patches.single { it.clipId == "body" }.propertyAnimations
            .single { it.propertyId == "transform.x" }
        assertTrue(x.keyframes.all { (it.value as PropertyValue.Scalar).value == 0.0 })
        val history = HistoryManager(original)
        history.execute(ApplyAnimationPlanCommand(plan))
        assertTrue(history.project.findClip("ra")!!.propertyAnimations.isNotEmpty())
        assertTrue(runCatching { LayeredCharacterActionPolicy.plan(history.project, rig,
            CharacterAction.WALK, 2_000_000L, 0.7) }.isFailure)
        assertTrue(history.undo())
        assertEquals(original, history.project)
    }

    @Test fun rejectsLockedShiftedDuplicateAndMissingParts() {
        listOf(project(locked = true), project(shifted = true)).forEach {
            assertTrue(runCatching { LayeredCharacterActionPolicy.plan(it, rig,
                CharacterAction.WALK, 2_000_000L, 0.7) }.isFailure)
        }
        assertTrue(runCatching { LayeredCharacterActionPolicy.plan(project(),
            rig.copy(leftArmClipId = "ra"), CharacterAction.WALK, 2_000_000L, 0.7) }.isFailure)
        assertTrue(runCatching { LayeredCharacterActionPolicy.plan(project(),
            rig.copy(rightArmClipId = null), CharacterAction.WALK, 2_000_000L, 0.7) }.isFailure)
        assertTrue(runCatching { LayeredCharacterActionPolicy.plan(project(), rig,
            CharacterAction.WALK, 6_000_000L, 0.7) }.isFailure)
    }
}
