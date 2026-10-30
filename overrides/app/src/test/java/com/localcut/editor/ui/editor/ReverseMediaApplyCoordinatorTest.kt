package com.localcut.editor.ui.editor

import com.localcut.editor.domain.history.EditorCommand
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseMediaApplyCoordinatorTest {
    @Test
    fun appliesPreparedReverseThroughNormalEditorCommandPath() = runBlocking {
        var project = project()
        val generated = Files.createTempFile("localcut-reverse", ".mp4").toFile().apply { writeText("reversed") }
        val reversedAsset = videoAsset("reversed", "content://reversed", "reverse-fp")
        val coordinator = ReverseMediaApplyCoordinator(
            currentProject = { project },
            prepareReverse = { Result.success(PreparedReverseAsset(reversedAsset, generated)) },
            execute = { command -> project = command.apply(project).project },
        )

        val result = coordinator.reverse("clip").getOrThrow()

        assertEquals("reversed", project.findClip("clip")?.assetId)
        assertEquals("reversed", result.asset.id)
        assertTrue(generated.exists())
        assertTrue(generated.delete())
    }

    @Test
    fun deletesGeneratedFileWhenSourceBecomesStaleBeforeApply() = runBlocking {
        var project = project()
        val generated = Files.createTempFile("localcut-reverse-stale", ".mp4").toFile().apply { writeText("reversed") }
        val reversedAsset = videoAsset("reversed", "content://reversed", "reverse-fp")
        val coordinator = ReverseMediaApplyCoordinator(
            currentProject = { project },
            prepareReverse = {
                val original = requireNotNull(project.findClip("clip"))
                project = project.withClip(original.copy(sourceInUs = original.sourceInUs + 100_000L))
                Result.success(PreparedReverseAsset(reversedAsset, generated))
            },
            execute = { error("must not execute stale result") },
        )

        val result = coordinator.reverse("clip")

        assertTrue(result.isFailure)
        assertEquals("REVERSE_RESULT_STALE", result.exceptionOrNull()?.message)
        assertFalse(generated.exists())
    }

    @Test
    fun deletesGeneratedFileWhenEditorRejectsReplacement() = runBlocking {
        val project = project()
        val generated = Files.createTempFile("localcut-reverse-rejected", ".mp4").toFile().apply { writeText("reversed") }
        val reversedAsset = videoAsset("reversed", "content://reversed", "reverse-fp")
        val coordinator = ReverseMediaApplyCoordinator(
            currentProject = { project },
            prepareReverse = { Result.success(PreparedReverseAsset(reversedAsset, generated)) },
            execute = { _: EditorCommand -> Unit },
        )

        val result = coordinator.reverse("clip")

        assertTrue(result.isFailure)
        assertEquals("REVERSE_APPLY_REJECTED", result.exceptionOrNull()?.message)
        assertFalse(generated.exists())
    }

    private fun project(): Project {
        val asset = videoAsset("original", "content://original", "original-fp")
        val clip = Clip(
            id = "clip",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 5_000_000L,
            sourceInUs = 2_000_000L,
            sourceOutUs = 7_000_000L,
        )
        return Project.create("Reverse", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }

    private fun videoAsset(id: String, uri: String, fingerprint: String) = Asset(
        id = id,
        type = AssetType.VIDEO,
        uri = uri,
        sourceDurationUs = 10_000_000L,
        sizeBytes = 8L,
        displayName = "$id.mp4",
        mimeType = "video/mp4",
        fingerprint = fingerprint,
    )

    private fun Project.withClip(replacement: Clip): Project = copy(
        tracks = tracks.map { track ->
            track.copy(clips = track.clips.map { if (it.id == replacement.id) replacement else it })
        },
    )
}
