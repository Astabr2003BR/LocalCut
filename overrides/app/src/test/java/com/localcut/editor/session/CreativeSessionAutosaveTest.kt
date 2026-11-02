package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeSessionAutosaveTest {
    @Test
    fun creativeEditsUndoRedoAndAutosaveStayOnTheSameSessionPath() {
        val saved = mutableListOf<Project>()
        val saver = DebouncedProjectSaver(
            store = object : ProjectStore {
                override fun save(project: Project): Result<Unit> {
                    saved += project
                    return Result.success(Unit)
                }

                override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
            },
            scheduler = object : DelayScheduler {
                override fun schedule(delayMs: Long, action: () -> Unit): Cancellable = Cancellable { }
            },
            delayMs = 300L,
        )
        val session = ProjectSession(
            initialProject = projectWithClip(),
            preview = object : ProjectPreviewLoader {
                override fun load(project: Project): Result<Unit> = Result.success(Unit)
            },
            autosave = saver,
        )
        session.dispatch(EditorAction.SelectClip("clip-1"))

        val effects = listOf(
            EffectInstance(
                id = "brightness",
                typeId = "localcut.color.brightness",
                parameters = mapOf("amount" to EffectParameter.Number(0.2)),
            ),
        )
        session.dispatch(EditorAction.SetSelectedEffects(effects))
        assertTrue(saver.flushNow().isSuccess)
        assertEquals(effects, saved.last().findClip("clip-1")!!.effects)

        val compositing = CompositingSettings(blendMode = BlendMode.SCREEN)
        session.dispatch(EditorAction.SetSelectedCompositing(compositing))
        assertTrue(saver.flushNow().isSuccess)
        assertEquals(compositing, saved.last().findClip("clip-1")!!.compositing)

        val timeMap = TimeMap(baseSpeed = 2.0)
        session.dispatch(EditorAction.SetSelectedTimeMap(timeMap))
        assertTrue(saver.flushNow().isSuccess)
        assertEquals(timeMap, saved.last().findClip("clip-1")!!.timeMap)

        val audioProcessing = AudioProcessingSettings(normalizationGainDb = 2.0)
        session.dispatch(EditorAction.SetSelectedAudioProcessing(audioProcessing))
        assertTrue(saver.flushNow().isSuccess)
        assertEquals(audioProcessing, saved.last().findClip("clip-1")!!.audioProcessing)

        val animations = listOf(
            PropertyAnimation(
                propertyId = "transform.x",
                keyframes = listOf(
                    PropertyKeyframe("a", 0L, PropertyValue.Scalar(0.0)),
                    PropertyKeyframe("b", 1_000_000L, PropertyValue.Scalar(100.0)),
                ),
            ),
        )
        session.dispatch(EditorAction.SetSelectedPropertyAnimations(animations))
        assertTrue(saver.flushNow().isSuccess)
        assertEquals(animations, saved.last().findClip("clip-1")!!.propertyAnimations)

        session.dispatch(EditorAction.Undo)
        assertTrue(saver.flushNow().isSuccess)
        assertTrue(saved.last().findClip("clip-1")!!.propertyAnimations.isEmpty())

        session.dispatch(EditorAction.Redo)
        assertTrue(saver.flushNow().isSuccess)
        assertEquals(animations, saved.last().findClip("clip-1")!!.propertyAnimations)
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
        return Project.create("Creative autosave", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
