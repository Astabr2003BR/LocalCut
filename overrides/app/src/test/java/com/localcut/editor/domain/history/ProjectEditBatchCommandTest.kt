package com.localcut.editor.domain.history

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.DesignElement
import com.localcut.editor.domain.model.PathPoint
import com.localcut.editor.domain.model.ShapeKind
import com.localcut.editor.domain.model.ShapeSpec
import com.localcut.editor.domain.model.VectorPath
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TextOverlay
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEditBatchCommandTest {
    @Test
    fun deleteAudioAndTextPreservesVisualsAndSingleUndoRestoresExactProject() {
        val original = fixtureProject()
        val result = ProjectEditBatchCommand(
            listOf(
                ProjectEditOperation.DeleteByKind(setOf(ProjectItemKind.AUDIO, ProjectItemKind.TEXT)),
            ),
        ).apply(original)

        assertNotNull(result.project.findClip("video-clip"))
        assertNotNull(result.project.findClip("image-clip"))
        assertNull(result.project.findClip("audio-clip"))
        assertTrue(result.project.textOverlays.isEmpty())
        assertEquals(original, result.inverse.apply(result.project).project)
    }

    @Test
    fun addClipReusesExistingAssetAndSingleUndoRestoresExactProject() {
        val original = fixtureProject()
        val result = ProjectEditBatchCommand(
            listOf(
                ProjectEditOperation.AddClip(
                    clipId = "image-copy",
                    assetId = "image",
                    trackId = "visual-1",
                    startUs = 2_000_000L,
                    durationUs = 1_000_000L,
                    scale = 0.75,
                    opacity = 0.8,
                ),
            ),
        ).apply(original)

        val added = result.project.findClip("image-copy")
        assertNotNull(added)
        assertEquals("image", added!!.assetId)
        assertEquals(2_000_000L, added.startUs)
        assertEquals(0.75, added.transform.scale, 0.0)
        assertEquals(0.8, added.opacity, 0.0)
        assertEquals(4, result.project.tracks.sumOf { it.clips.size })
        assertEquals(original, result.inverse.apply(result.project).project)
    }

    @Test
    fun patchClipOpacityUsesClipPropertyWithoutOverwritingTransformOpacityOrKeyframes() {
        val original = fixtureProject()
        val video = requireNotNull(original.findClip("video-clip"))
        val withTransformOpacity = original.copy(tracks = original.tracks.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == video.id) clip.copy(transform = clip.transform.copy(opacity = 0.6), opacity = 0.9) else clip
            })
        })
        val before = requireNotNull(withTransformOpacity.findClip(video.id))
        val result = ProjectEditBatchCommand(listOf(ProjectEditOperation.PatchClip(video.id, opacity = 0.8))).apply(withTransformOpacity)
        val after = requireNotNull(result.project.findClip(video.id))
        assertEquals(0.8, after.opacity, 0.0)
        assertEquals(before.copy(opacity = 0.8), after)
        assertEquals(withTransformOpacity, result.inverse.apply(result.project).project)
        assertTrue(runCatching { ProjectEditBatchCommand(listOf(ProjectEditOperation.PatchClip(video.id, opacity = 1.1))).apply(withTransformOpacity) }.isFailure)
    }

    @Test
    fun mixedPatchUpdatesOnlySpecifiedFields() {
        val original = fixtureProject()
        val result = ProjectEditBatchCommand(
            listOf(
                ProjectEditOperation.PatchClip(
                    clipId = "video-clip",
                    startUs = 2_000_000L,
                    scale = 1.5,
                    volume = 0.75,
                ),
                ProjectEditOperation.PatchText(
                    overlayId = "text-1",
                    text = "Novo texto",
                    opacity = 0.5,
                    fontSizePx = 72.0,
                ),
            ),
        ).apply(original).project

        val clip = result.findClip("video-clip")!!
        assertEquals(2_000_000L, clip.startUs)
        assertEquals(1.5, clip.transform.scale, 0.0)
        assertEquals(0.75, clip.volume, 0.0)

        val text = result.textOverlays.single()
        assertEquals("Novo texto", text.text)
        assertEquals(0.5, text.opacity, 0.0)
        assertEquals(72.0, text.style.fontSizePx, 0.0)
        assertEquals(1_500_000L, text.durationUs)
        assertEquals(original.textOverlays.single().startUs, text.startUs)
    }

    @Test
    fun laterInvalidOperationFailsWholeBatchWithoutMutatingInputProject() {
        val original = fixtureProject()

        val failure = runCatching {
            ProjectEditBatchCommand(
                listOf(
                    ProjectEditOperation.DeleteClip("video-clip"),
                    ProjectEditOperation.PatchClip("video-clip", startUs = 3_000_000L),
                ),
            ).apply(original)
        }

        assertTrue(failure.isFailure)
        assertNotNull(original.findClip("video-clip"))
        assertEquals(0L, original.findClip("video-clip")!!.startUs)
        assertEquals(3, original.tracks.sumOf { it.clips.size })
    }

    @Test
    fun invalidTextPatchIsRejectedByProjectValidation() {
        val original = fixtureProject()

        val failure = runCatching {
            ProjectEditBatchCommand(
                listOf(ProjectEditOperation.PatchText("text-1", durationUs = 0L)),
            ).apply(original)
        }

        assertTrue(failure.isFailure)
        assertEquals(1_500_000L, original.textOverlays.single().durationUs)
    }

    @Test
    fun addPatchAndDeleteDesignAreAtomicAndUndoable() {
        val original = fixtureProject()
        val element = DesignElement(
            id = "stroke-remote",
            startUs = 250_000L,
            durationUs = 2_000_000L,
            shape = ShapeSpec(
                kind = ShapeKind.PATH,
                path = VectorPath(
                    points = listOf(PathPoint(0.1, 0.2), PathPoint(0.8, 0.7)),
                    closed = false,
                ),
                fillArgb = null,
                strokeArgb = 0xFFFF0000.toInt(),
                strokeWidthPx = 12.0,
            ),
            zIndex = 3,
        )
        val added = ProjectEditBatchCommand(
            listOf(ProjectEditOperation.AddDesign(element)),
        ).apply(original)
        assertEquals(element, added.project.designElements.single())

        val patched = ProjectEditBatchCommand(
            listOf(
                ProjectEditOperation.PatchDesign(
                    elementId = "stroke-remote",
                    x = 120.0,
                    scale = 1.5,
                    opacity = 0.6,
                    strokeWidthPx = 20.0,
                    zIndex = 8,
                ),
            ),
        ).apply(added.project)
        val changed = patched.project.designElements.single()
        assertEquals(120.0, changed.transform.x, 0.0)
        assertEquals(1.5, changed.transform.scale, 0.0)
        assertEquals(0.6, changed.opacity, 0.0)
        assertEquals(20.0, changed.shape!!.strokeWidthPx, 0.0)
        assertEquals(8, changed.zIndex)

        val deleted = ProjectEditBatchCommand(
            listOf(ProjectEditOperation.DeleteByKind(setOf(ProjectItemKind.DESIGN))),
        ).apply(patched.project)
        assertTrue(deleted.project.designElements.isEmpty())
        assertEquals(patched.project, deleted.inverse.apply(deleted.project).project)
    }

    @Test
    fun invalidDesignPatchFailsClosedWithoutMutatingInput() {
        val original = fixtureProject().copy(
            designElements = listOf(
                DesignElement(
                    id = "shape-1",
                    startUs = 0L,
                    durationUs = 1_000_000L,
                    shape = ShapeSpec(ShapeKind.RECTANGLE),
                ),
            ),
        )
        val failure = runCatching {
            ProjectEditBatchCommand(
                listOf(ProjectEditOperation.PatchDesign("shape-1", scale = 0.0)),
            ).apply(original)
        }
        assertTrue(failure.isFailure)
        assertEquals(1.0, original.designElements.single().transform.scale, 0.0)
    }

    @Test
    fun batchTrimAndTextPatchPreserveOtherItemsAndUndoExactly() {
        val original = fixtureProject()
        val operations = listOf(
            ProjectEditOperation.TrimClip("video-clip", newEndUs = 500_000L),
            ProjectEditOperation.PatchText("text-1", text = "Novo"),
        )
        val result = ProjectEditBatchCommand(operations).apply(original)
        val clip = result.project.findClip("video-clip")!!
        assertEquals(500_000L, clip.durationUs)
        assertEquals(500_000L, clip.sourceOutUs)
        assertEquals(original.findClip("image-clip"), result.project.findClip("image-clip"))
        assertEquals(original.findClip("audio-clip"), result.project.findClip("audio-clip"))
        assertEquals(original.assets, result.project.assets)
        assertEquals("Novo", result.project.textOverlays.single().text)
        val undo = result.inverse.apply(result.project)
        assertEquals(original, undo.project)
        assertEquals(result.project, undo.inverse.apply(undo.project).project)
    }

    @Test
    fun clipBatchRejectsTrackAndLayerLocksIncludingBulkDelete() {
        val baseline = fixtureProject()
        val lockedTrack = baseline.copy(tracks = baseline.tracks.map {
            if (it.id == "visual-1") it.copy(locked = true) else it
        })
        val lockedLayer = baseline.copy(compositionGraph = CompositionGraph(nodes = listOf(
            CompositionNode(id = "layer-video", kind = CompositionNodeKind.LAYER,
                clipId = "video-clip", locked = true),
        )))
        val blocked = listOf(
            ProjectEditOperation.PatchClip("video-clip", volume = 0.5),
            ProjectEditOperation.TrimClip("video-clip", newEndUs = 500_000L),
            ProjectEditOperation.DeleteClip("video-clip"),
            ProjectEditOperation.DeleteByKind(setOf(ProjectItemKind.VIDEO, ProjectItemKind.TEXT)),
        )
        for (project in listOf(lockedTrack, lockedLayer)) {
            for (operation in blocked) {
                val result = runCatching {
                    ProjectEditBatchCommand(listOf(ProjectEditOperation.PatchText("text-1", text = "Not committed"), operation)).apply(project)
                }
                assertTrue(result.isFailure)
                assertEquals(baseline.findClip("video-clip"), project.findClip("video-clip"))
                assertEquals(baseline.textOverlays, project.textOverlays)
            }
        }
    }

    @Test
    fun trimRejectsMissingAmbiguousNegativeAndOutOfRangeEdgesAtomically() {
        val project = fixtureProject()
        val cases = listOf(
            ProjectEditOperation.TrimClip("video-clip"),
            ProjectEditOperation.TrimClip("video-clip", 100_000L, 500_000L),
            ProjectEditOperation.TrimClip("video-clip", newStartUs = -1L),
            ProjectEditOperation.TrimClip("video-clip", newEndUs = 10_000_000L),
            ProjectEditOperation.TrimClip("missing", newEndUs = 500_000L),
        )
        for (operation in cases) {
            assertTrue(runCatching { ProjectEditBatchCommand(listOf(operation)).apply(project) }.isFailure)
        }
        assertEquals(1_000_000L, project.findClip("video-clip")!!.durationUs)
    }

    @Test
    fun effectPatchesAndKeyframesPreserveOtherEffectsAndExactUndo() {
        val baseline = fixtureProject()
        val existing = EffectInstance("brightness", "localcut.color.brightness", parameters = mapOf("amount" to EffectParameter.Number(0.1)),
            animations = listOf(PropertyAnimation("amount", listOf(
                PropertyKeyframe("first", 0L, PropertyValue.Scalar(0.1)),
                PropertyKeyframe("last", 1_000_000L, PropertyValue.Scalar(0.2)),
            ))))
        val original = baseline.copy(tracks = baseline.tracks.map { track -> track.copy(clips = track.clips.map {
            if (it.id == "video-clip") it.copy(effects = listOf(existing), volume = 0.7) else it
        }) })
        val result = ProjectEditBatchCommand(listOf(
            ProjectEditOperation.AddEffect("video-clip", "contrast", "localcut.color.contrast", mapOf("amount" to 1.2)),
            ProjectEditOperation.PatchEffect("video-clip", "contrast", numbers = mapOf("amount" to 1.5)),
            ProjectEditOperation.SetEffectKeyframe("video-clip", "brightness", "amount", 500_000L, 0.4),
            ProjectEditOperation.SetEffectKeyframe("video-clip", "brightness", "amount", 0L, 0.3),
        )).apply(original)
        val clip = result.project.findClip("video-clip")!!
        assertEquals(listOf("brightness", "contrast"), clip.effects.map { it.id })
        assertEquals(EffectParameter.Number(1.5), clip.effects[1].parameters["amount"])
        val frames = clip.effects[0].animations.single().keyframes
        assertEquals(listOf(0L, 500_000L, 1_000_000L), frames.map { it.offsetUs })
        assertEquals("first", frames.first().id)
        assertEquals(existing.animations.single().keyframes.last(), frames.last())
        assertEquals(0.7, clip.volume, 0.0)
        assertEquals(original.findClip("audio-clip"), result.project.findClip("audio-clip"))
        assertEquals(original.textOverlays, result.project.textOverlays)
        assertEquals(original, result.inverse.apply(result.project).project)
    }

    @Test
    fun invalidEffectsAndLockedAncestorRejectWholeBatch() {
        val original = fixtureProject()
        val add = ProjectEditOperation.AddEffect("video-clip", "brightness", "localcut.color.brightness", mapOf("amount" to 0.2))
        val withEffect = ProjectEditBatchCommand(listOf(add)).apply(original).project
        val invalid = listOf(
            ProjectEditOperation.AddEffect("video-clip", "unknown", "arbitrary.shader"),
            ProjectEditOperation.AddEffect("video-clip", "bad", "localcut.color.brightness", mapOf("amount" to 2.0)),
            ProjectEditOperation.AddEffect("audio-clip", "bad", "localcut.color.brightness"),
            add,
            ProjectEditOperation.PatchEffect("video-clip", "brightness", numbers = mapOf("unknown" to 0.2)),
            ProjectEditOperation.SetEffectKeyframe("video-clip", "brightness", "amount", 1_000_001L, 0.1),
            ProjectEditOperation.SetEffectKeyframe("video-clip", "brightness", "amount", 0L, 2.0),
        )
        for (op in invalid) {
            assertTrue(runCatching { ProjectEditBatchCommand(listOf(ProjectEditOperation.PatchText("text-1", text = "Not committed"), op)).apply(withEffect) }.isFailure)
        }
        val locked = original.copy(compositionGraph = CompositionGraph(nodes = listOf(
            CompositionNode(id = "group", kind = CompositionNodeKind.GROUP, locked = true),
            CompositionNode(id = "layer-video", kind = CompositionNodeKind.LAYER, parentId = "group", clipId = "video-clip"),
        )))
        assertTrue(runCatching { ProjectEditBatchCommand(listOf(add)).apply(locked) }.isFailure)
        assertTrue(locked.findClip("video-clip")!!.effects.isEmpty())
        assertEquals(original.textOverlays, withEffect.textOverlays)
    }

    @Test
    fun replaceStillBackgroundPreservesAllOtherProjectFieldsAndUndo() {
        val baseline = fixtureProject()
        val original = baseline.copy(assets = baseline.assets + asset("city-new", AssetType.IMAGE, "city.png", "image/png", null))
        val command = ReplaceStillImageAssetCommand("image-clip", "image", "city-new")
        val result = command.apply(original)
        val expected = original.copy(tracks = original.tracks.map { track ->
            track.copy(clips = track.clips.map { clip ->
                if (clip.id == "image-clip") clip.copy(assetId = "city-new") else clip
            })
        })
        assertEquals(expected, result.project)
        assertEquals(original, result.inverse.apply(result.project).project)
        assertEquals(expected, command.apply(original).project)
        val invalid = listOf(
            ReplaceStillImageAssetCommand("video-clip", "video", "city-new"),
            ReplaceStillImageAssetCommand("image-clip", "image", "missing"),
            ReplaceStillImageAssetCommand("image-clip", "wrong", "city-new"),
            ReplaceStillImageAssetCommand("image-clip", "image", "image"),
        )
        invalid.forEach { assertTrue(runCatching { it.apply(original) }.isFailure) }
        val locked = original.copy(tracks = original.tracks.map { if (it.id == "visual-1") it.copy(locked = true) else it })
        assertTrue(runCatching { command.apply(locked) }.isFailure)
    }

    private fun fixtureProject(): Project {
        val video = asset("video", AssetType.VIDEO, "video.mp4", "video/mp4", 5_000_000L)
        val image = asset("image", AssetType.IMAGE, "image.png", "image/png", null)
        val audio = asset("audio", AssetType.AUDIO, "audio.m4a", "audio/mp4", 5_000_000L)
        return Project(
            id = "p",
            schemaVersion = BuildContract.PROJECT_SCHEMA_VERSION,
            title = "test",
            width = 1080,
            height = 1920,
            fps = 30,
            assets = listOf(video, image, audio),
            tracks = listOf(
                Track(
                    "visual-1",
                    TrackType.VISUAL,
                    0,
                    clips = listOf(
                        Clip("video-clip", "video", 0L, 1_000_000L, 0L, 1_000_000L),
                        Clip("image-clip", "image", 1_000_000L, 1_000_000L, 0L, 1_000_000L),
                    ),
                ),
                Track(
                    "audio-1",
                    TrackType.AUDIO,
                    1,
                    clips = listOf(Clip("audio-clip", "audio", 0L, 1_000_000L, 0L, 1_000_000L)),
                ),
            ),
            textOverlays = listOf(TextOverlay("text-1", 0L, 1_500_000L, "Texto")),
        )
    }

    private fun asset(
        id: String,
        type: AssetType,
        name: String,
        mimeType: String,
        durationUs: Long?,
    ): Asset = Asset(
        id = id,
        type = type,
        uri = "content://private/$name",
        sourceDurationUs = durationUs,
        sizeBytes = 100L,
        displayName = name,
        mimeType = mimeType,
        fingerprint = "fp-$id",
    )
}
