package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeClipCommandsTest {
    @Test
    fun effectsCompositingAndPropertyAnimationAreUndoable() {
        val initial = projectWithClip()
        val history = HistoryManager(initial)
        val effect = EffectInstance(
            id = "fx-1",
            typeId = "localcut.color.brightness",
            parameters = mapOf("amount" to EffectParameter.Number(0.25)),
        )
        val compositing = CompositingSettings(blendMode = BlendMode.SCREEN)
        val animation = PropertyAnimation(
            propertyId = "transform.x",
            keyframes = listOf(
                PropertyKeyframe("a", 0L, PropertyValue.Scalar(0.0)),
                PropertyKeyframe("b", 4_000_000L, PropertyValue.Scalar(100.0)),
            ),
        )

        history.execute(SetClipEffectsCommand("clip-1", listOf(effect)))
        assertEquals(listOf(effect), history.project.findClip("clip-1")!!.effects)
        history.execute(SetClipCompositingCommand("clip-1", compositing))
        assertEquals(compositing, history.project.findClip("clip-1")!!.compositing)
        history.execute(SetClipPropertyAnimationsCommand("clip-1", listOf(animation)))
        assertEquals(listOf(animation), history.project.findClip("clip-1")!!.propertyAnimations)

        assertTrue(history.undo())
        assertTrue(history.project.findClip("clip-1")!!.propertyAnimations.isEmpty())
        assertTrue(history.undo())
        assertEquals(CompositingSettings(), history.project.findClip("clip-1")!!.compositing)
        assertTrue(history.undo())
        assertTrue(history.project.findClip("clip-1")!!.effects.isEmpty())
        assertEquals(initial, history.project)
    }

    @Test
    fun timeMapCommandUpdatesPresentationDurationAndUndoRestoresClip() {
        val initial = projectWithClip()
        val history = HistoryManager(initial)
        val map = TimeMap(baseSpeed = 2.0)

        history.execute(SetClipTimeMapCommand("clip-1", map))

        val spedUp = history.project.findClip("clip-1")!!
        assertEquals(2_000_000L, spedUp.durationUs)
        assertEquals(map, spedUp.timeMap)

        assertTrue(history.undo())
        assertEquals(initial.findClip("clip-1"), history.project.findClip("clip-1"))
    }

    @Test
    fun clipOpacityIsUndoableRedoableAndBounded() {
        val initial = projectWithClip()
        val history = HistoryManager(initial)

        history.execute(SetClipOpacityCommand("clip-1", 0.35))
        assertEquals(0.35, history.project.findClip("clip-1")!!.opacity, 0.0)

        assertTrue(history.undo())
        assertEquals(1.0, history.project.findClip("clip-1")!!.opacity, 0.0)

        assertTrue(history.redo())
        assertEquals(0.35, history.project.findClip("clip-1")!!.opacity, 0.0)

        assertThrows(IllegalArgumentException::class.java) {
            SetClipOpacityCommand("clip-1", 1.1).apply(initial)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SetClipOpacityCommand("clip-1", Double.NaN).apply(initial)
        }
    }

    @Test
    fun lockedLayerRejectsStaticOpacityMutation() {
        val initial = projectWithClip().copy(
            compositionGraph = CompositionGraph(
                nodes = listOf(
                    CompositionNode(
                        id = "layer-clip-1",
                        kind = CompositionNodeKind.LAYER,
                        clipId = "clip-1",
                        locked = true,
                    ),
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            SetClipOpacityCommand("clip-1", 0.5).apply(initial)
        }
        assertEquals(1.0, initial.findClip("clip-1")!!.opacity, 0.0)
    }

    private fun projectWithClip(): Project {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 4_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 4_000_000L,
        )
        return Project.create("Creative", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
