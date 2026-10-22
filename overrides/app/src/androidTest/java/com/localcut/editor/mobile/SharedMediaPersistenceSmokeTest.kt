package com.localcut.editor.mobile

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.data.assets.AndroidAssetImporter
import com.localcut.editor.data.project.AtomicProjectStore
import com.localcut.editor.domain.model.Project
import com.localcut.editor.media.PreviewController
import com.localcut.editor.media.PreviewState
import com.localcut.editor.session.Cancellable
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.DelayScheduler
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import com.localcut.editor.ui.editor.EditorViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMediaPersistenceSmokeTest {
    @Test
    fun sharedImageIsPersistedBeforeImportReturns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projectDir = context.cacheDir.toPath().resolve("persistence-smoke-${UUID.randomUUID()}")
        val store = AtomicProjectStore(projectDir)
        val scheduler = DelayScheduler { _, _ -> Cancellable { } }
        val autosave = DebouncedProjectSaver(store, scheduler, 60_000L)
        val previewController = NoOpPreviewController()
        val session = ProjectSession(
            initialProject = Project.create("Persistence smoke", 1080, 1920, 30),
            preview = ProjectPreviewLoader { Result.success(Unit) },
            autosave = autosave,
            store = store,
        )
        val editorViewModel = EditorViewModel(session, previewController)
        val coordinator = MediaIngestCoordinator(
            importer = AndroidAssetImporter(context.contentResolver),
            editorViewModel = editorViewModel,
            sharedMediaStager = SharedMediaStager(context),
            persistProject = autosave::flushNow,
        )

        val remoteDir = File(context.filesDir, "remote-media")
        val beforePrivateFiles = remoteDir.listFiles().orEmpty().map { it.name }.toSet()
        val sourceUri = createPngFixture()
        try {
            val result = runBlocking(Dispatchers.Main.immediate) {
                coordinator.importSharedUris(listOf(sourceUri))
            }
            assertEquals(result.errors.joinToString(), 1, result.importedCount)
            assertEquals(result.errors.joinToString(), 0, result.failedCount)
            assertTrue("successful import must surface no persistence error: ${result.errors}", result.errors.isEmpty())
            assertTrue(
                "autosave must report success before import returns: ${autosave.lastResult?.exceptionOrNull()}",
                autosave.lastResult?.isSuccess == true,
            )

            val importedAsset = session.state.project.assets.single()
            val persisted = store.load().getOrThrow()
            assertTrue(
                "successful import must already be present in project.json before importSharedUris returns",
                persisted.assets.any { it.id == importedAsset.id },
            )
            assertTrue(
                "persisted project must retain the imported clip",
                persisted.tracks.any { track -> track.clips.any { it.assetId == importedAsset.id } },
            )
            assertTrue(
                "project.json must exist after a successful shared-media import",
                projectDir.resolve("project.json").toFile().isFile,
            )
        } finally {
            runCatching { context.contentResolver.delete(sourceUri, null, null) }
            projectDir.toFile().deleteRecursively()
            remoteDir.listFiles().orEmpty()
                .filter { it.name !in beforePrivateFiles }
                .forEach { runCatching { it.delete() } }
        }
    }

    private class NoOpPreviewController : PreviewController {
        private val mutableState = MutableStateFlow(PreviewState())
        override val state: StateFlow<PreviewState> = mutableState

        override fun load(project: Project): Result<Unit> = Result.success(Unit)
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekToUs(positionUs: Long) = Unit
        override fun stepFrames(deltaFrames: Int, fps: Int) = Unit
        override fun setLoopRange(startUs: Long, endUs: Long?) = Unit
        override fun release() = Unit
    }

    private fun createPngFixture(): Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "localcut-persistence-${UUID.randomUUID()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                },
            ),
        ) { "failed to create MediaStore fixture" }
        resolver.openOutputStream(uri)?.use { output ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            try {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } finally {
                bitmap.recycle()
            }
        } ?: error("failed to write MediaStore fixture")
        return uri
    }
}
