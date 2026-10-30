package com.localcut.editor.projectpackage

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPackageContractTest {
    @Test
    fun pathPolicyRejectsTraversalAbsoluteAndBackslashPaths() {
        listOf("../evil", "assets/../../evil", "/absolute/file", "assets\\evil.mp4", "C:/evil").forEach { path ->
            assertFalse("expected rejection for $path", ProjectPackagePathPolicy.isSafe(path))
        }
        assertTrue(ProjectPackagePathPolicy.isSafe("assets/video-1/clip.mp4"))
        assertTrue(ProjectPackagePathPolicy.isSafe("project.json"))
    }

    @Test
    fun plannerBuildsDeterministicSafeAssetEntries() {
        val project = Project.create("Package", 1080, 1920, 30).copy(
            assets = listOf(
                Asset("video-1", AssetType.VIDEO, "content://video", 1_000_000L, 10L, "../bad name.mp4", "video/mp4", "fp-v"),
                Asset("audio-1", AssetType.AUDIO, "content://audio", 1_000_000L, 5L, "voice.wav", "audio/wav", "fp-a"),
            ),
        )

        val manifest = ProjectPackagePlanner.plan(project)

        assertEquals("project.json", manifest.projectJsonPath)
        assertEquals(listOf("audio-1", "video-1"), manifest.assets.map { it.assetId })
        assertTrue(manifest.assets.all { ProjectPackagePathPolicy.isSafe(it.path) })
        assertTrue(manifest.assets.single { it.assetId == "video-1" }.path.endsWith("bad_name.mp4"))
    }

    @Test
    fun importValidatorReportsMissingEntriesWithoutAcceptingUnsafeManifest() {
        val manifest = ProjectPackageManifest(
            schemaVersion = 1,
            projectJsonPath = "project.json",
            assets = listOf(
                ProjectPackageAsset("v", "assets/v/video.mp4", "fp-v", 10L),
                ProjectPackageAsset("a", "assets/a/audio.wav", "fp-a", 5L),
            ),
        )

        val result = ProjectPackageValidator.validate(manifest, availableEntries = setOf("project.json", "assets/v/video.mp4"))
        assertEquals(listOf("assets/a/audio.wav"), result.missingEntries)
        assertTrue(result.issues.isEmpty())

        val unsafe = manifest.copy(projectJsonPath = "../project.json")
        assertTrue(ProjectPackageValidator.validate(unsafe, emptySet()).issues.isNotEmpty())
    }
}
