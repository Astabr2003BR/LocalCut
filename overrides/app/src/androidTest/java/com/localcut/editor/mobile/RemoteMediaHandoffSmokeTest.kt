package com.localcut.editor.mobile

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.TrackType
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteMediaHandoffSmokeTest {
    @Test
    fun remoteImageFlowsThroughFileProviderIntoTimelineAndKeepsPersistentBacking() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var runtime: LocalCutProcessRuntime
        instrumentation.runOnMainSync {
            runtime = LocalCutProcessRuntime.get(context)
        }
        val before = visualClipCount(runtime)
        val beforeAssetIds = runtime.session.state.project.assets.map { it.id }.toSet()
        val persistentDir = File(context.filesDir, "remote-media").apply { mkdirs() }
        val persistentBefore = persistentDir.listFiles().orEmpty().map { it.name }.toSet()
        val remoteDir = File(context.cacheDir, "remote-import").apply { mkdirs() }
        val partial = createPng(File(remoteDir, "remote-handoff-${System.nanoTime()}.part"))

        val executor = RemoteMediaImportExecutor(
            context = context,
            mediaIngestCoordinator = runtime.mediaIngestCoordinator,
            downloader = { _ ->
                Result.success(
                    DownloadedRemoteMedia(
                        file = partial,
                        declaredContentType = "image/png",
                        byteCount = partial.length(),
                    ),
                )
            },
        )

        val result = withContext(Dispatchers.Main) {
            executor.importUrl("https://media.example/photo.png")
        }

        assertTrue(result.isSuccess)
        val ingest = result.getOrThrow()
        assertEquals(1, ingest.importedCount)
        assertEquals(0, ingest.failedCount)
        assertEquals(before + 1, visualClipCount(runtime))
        assertFalse("downloaded partial must be removed after ingest", partial.exists())
        assertTrue(
            "download cache must not keep promoted media",
            remoteDir.listFiles().orEmpty().none { it.name.startsWith("remote-handoff-") },
        )

        val newAssets = runtime.session.state.project.assets.filter { it.id !in beforeAssetIds }
        assertEquals(1, newAssets.size)
        val newAsset = newAssets.single()
        assertTrue(
            "asset URI must still resolve after temporary download cleanup",
            AssetUriResolver(context.contentResolver).resolve(newAsset).isSuccess,
        )
        val persistentAfter = persistentDir.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(
            "exactly one persistent private backing file must be retained",
            1,
            (persistentAfter - persistentBefore).size,
        )
    }

    @Test
    fun cancellationPropagatesCleansDownloadAndDoesNotPersistMedia() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var runtime: LocalCutProcessRuntime
        instrumentation.runOnMainSync {
            runtime = LocalCutProcessRuntime.get(context)
        }
        val persistentDir = File(context.filesDir, "remote-media").apply { mkdirs() }
        val persistentBefore = persistentDir.listFiles().orEmpty().map { it.name }.toSet()
        val remoteDir = File(context.cacheDir, "remote-import").apply { mkdirs() }
        val partial = createPng(File(remoteDir, "remote-cancel-${System.nanoTime()}.part"))
        val validationStarted = CompletableDeferred<Unit>()
        var returnedResult: Result<MediaIngestResult>? = null

        val executor = RemoteMediaImportExecutor(
            context = context,
            mediaIngestCoordinator = runtime.mediaIngestCoordinator,
            downloader = { _ ->
                Result.success(
                    DownloadedRemoteMedia(
                        file = partial,
                        declaredContentType = "image/png",
                        byteCount = partial.length(),
                    ),
                )
            },
            validator = { _, _ ->
                validationStarted.complete(Unit)
                awaitCancellation()
            },
        )

        val job = launch(Dispatchers.Main) {
            returnedResult = executor.importUrl("https://media.example/cancel.png")
        }
        validationStarted.await()
        job.cancelAndJoin()

        assertTrue("caller coroutine must remain cancelled", job.isCancelled)
        assertNull("cancellation must not be converted into a normal Result", returnedResult)
        assertFalse("downloaded partial must be removed after cancellation", partial.exists())
        val persistentAfter = persistentDir.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(
            "cancelled import must not retain persistent media",
            persistentBefore,
            persistentAfter,
        )
    }

    private fun visualClipCount(runtime: LocalCutProcessRuntime): Int =
        runtime.session.state.project.tracks
            .filter { it.type == TrackType.VISUAL }
            .sumOf { it.clips.size }

    private fun createPng(file: File): File {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
        return file
    }
}
