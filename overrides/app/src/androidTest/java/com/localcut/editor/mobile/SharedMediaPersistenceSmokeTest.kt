package com.localcut.editor.mobile

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.data.project.AtomicProjectStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class SharedMediaPersistenceSmokeTest {
    @Test
    fun sharedImageIsPersistedBeforeImportReturns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var runtime: LocalCutProcessRuntime
        instrumentation.runOnMainSync {
            runtime = LocalCutProcessRuntime.get(context)
            runtime.flushAutosave()
        }

        val beforeAssetIds = runtime.session.state.project.assets.map { it.id }.toSet()
        val sourceUri = createPngFixture()
        try {
            val result = runBlocking(Dispatchers.Main.immediate) {
                runtime.mediaIngestCoordinator.importSharedUris(listOf(sourceUri))
            }
            assertEquals(result.errors.joinToString(), 1, result.importedCount)
            assertEquals(result.errors.joinToString(), 0, result.failedCount)

            val importedAsset = runtime.session.state.project.assets
                .single { it.id !in beforeAssetIds }
            val persisted = AtomicProjectStore(
                context.filesDir.toPath().resolve("projects/default"),
            ).load().getOrThrow()

            assertTrue(
                "successful import must already be present in project.json before importSharedUris returns",
                persisted.assets.any { it.id == importedAsset.id },
            )
            assertTrue(
                "persisted project must retain the imported clip",
                persisted.tracks.any { track -> track.clips.any { it.assetId == importedAsset.id } },
            )
        } finally {
            runCatching { context.contentResolver.delete(sourceUri, null, null) }
        }
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
