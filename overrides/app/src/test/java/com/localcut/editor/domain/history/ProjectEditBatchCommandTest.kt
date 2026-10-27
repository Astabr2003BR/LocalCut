package com.localcut.editor.domain.history

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
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
