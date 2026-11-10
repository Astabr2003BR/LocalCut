package com.localcut.editor.mobile

import com.localcut.editor.domain.history.SetClipEffectsCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileBlinkHandoffExecutorTest {
    private val eyes = MobileBlinkRegion(0.37, 0.63, 0.36, 0.085, 0.042)

    private fun project(extraPortrait: Boolean = false, locked: Boolean = false): Project {
        val base = Project.create("Portrait", 1080, 1920, 30)
        val assets = listOf(
            Asset("image", AssetType.IMAGE, "content://image", null, 256,
                "portrait.png", "image/png", "img"),
            Asset("voice", AssetType.AUDIO, "content://voice", 5_000_000L, 256,
                "voice.mp3", "audio/mpeg", "voice"),
        )
        val talk = EffectInstance(
            id = "talk",
            typeId = "localcut.character.mouth_warp",
            parameters = mapOf(
                "center" to EffectParameter.Point(0.51, 0.49),
                "radiusX" to EffectParameter.Number(0.07),
                "radiusY" to EffectParameter.Number(0.025),
                "openness" to EffectParameter.Number(0.0),
            ),
        )
        return base.copy(assets = assets, tracks = base.tracks.map { track ->
            when (track.type) {
                TrackType.VISUAL -> track.copy(
                    locked = locked,
                    clips = listOf(Clip("portrait", "image", 0L, 5_000_000L, 0L, 5_000_000L,
                        effects = listOf(talk))) +
                        if (extraPortrait) listOf(Clip("other", "image",
                            6_000_000L, 2_000_000L, 0L, 2_000_000L)) else emptyList(),
                )
                TrackType.AUDIO -> track.copy(
                    clips = listOf(Clip("voice-clip", "voice", 0L, 5_000_000L, 0L, 5_000_000L)),
                )
            }
        })
    }

    @Test fun appliesNewEffectWithoutLosingTalkOrAudioAndUndoRestoresAll() {
        val before = project()
        val planned = MobileBlinkHandoffExecutor.plan(before, "voice-clip", eyes)
        assertEquals("portrait", planned.clipId)
        assertEquals(2, planned.effects.size)
        assertEquals("talk", planned.effects[0].id)
        val applied = SetClipEffectsCommand(planned.clipId, planned.effects).apply(before)
        val updated = applied.project
        assertEquals(before.tracks.first { it.type == TrackType.AUDIO },
            updated.tracks.first { it.type == TrackType.AUDIO })
        assertEquals(before.assets, updated.assets)
        assertEquals(2, updated.findClip("portrait")!!.effects.size)
        val undone = applied.inverse.apply(updated).project
        assertEquals(before, undone)
    }

    @Test fun sameBlinkCommandIsIdempotentAndOtherEffectsSurvive() {
        val initial = project()
        val first = MobileBlinkHandoffExecutor.plan(initial, null, eyes)
        val edited = SetClipEffectsCommand(first.clipId, first.effects).apply(initial).project
        val second = MobileBlinkHandoffExecutor.plan(edited, null, eyes)
        assertEquals(first.effects, second.effects)
        assertEquals(1, second.effects.count { it.typeId == "localcut.character.eye_blink" })
        assertTrue(second.effects.any { it.typeId == "localcut.character.mouth_warp" })
    }

    @Test fun customPresetValuesAreAppliedWithoutDuplicatingTalkOrAudio() {
        val custom = MobileBlinkRegion(
            0.37, 0.63, 0.36, 0.085, 0.042,
            intervalSeconds = 2.7,
            blinkStrength = 0.9,
            swayStrength = 0.4,
            breathStrength = 0.7,
        )
        val before = project()
        val first = MobileBlinkHandoffExecutor.plan(before, "voice-clip", custom)
        val applied = SetClipEffectsCommand(first.clipId, first.effects).apply(before).project
        val blink = applied.findClip("portrait")!!.effects.single {
            it.typeId == "localcut.character.eye_blink"
        }
        assertEquals(2.7, (blink.parameters["intervalSeconds"] as EffectParameter.Number).value, 0.0)
        assertEquals(0.7, (blink.parameters["breathStrength"] as EffectParameter.Number).value, 0.0)
        assertEquals(before.tracks.first { it.type == TrackType.AUDIO },
            applied.tracks.first { it.type == TrackType.AUDIO })
        assertEquals(first.effects, MobileBlinkHandoffExecutor.plan(applied, null, custom).effects)
    }

    @Test fun neverGuessesWhichImageToEditOrBypassesTrackLock() {
        assertTrue(runCatching {
            MobileBlinkHandoffExecutor.plan(project(extraPortrait = true), null, eyes)
        }.isFailure)
        assertTrue(runCatching {
            MobileBlinkHandoffExecutor.plan(project(locked = true), null, eyes)
        }.isFailure)
        assertTrue(runCatching {
            MobileBlinkHandoffExecutor.plan(project(), "different-image-id", eyes)
        }.isFailure) // Unknown selection cannot silently edit the wrong project.
    }
}
