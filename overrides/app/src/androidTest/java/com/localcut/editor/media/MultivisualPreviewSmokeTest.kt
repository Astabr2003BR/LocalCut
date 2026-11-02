package com.localcut.editor.media

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class MultivisualPreviewSmokeTest {
    @Test
    fun baseVideoAndOverlayImageCanLoadInFastPreview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.filesDir, "remote-media").apply { mkdirs() }
        val baseFile = createPng(File(directory, "preview-base-${System.nanoTime()}.png"), Color.BLUE)
        val overlayFile = createPng(File(directory, "preview-overlay-${System.nanoTime()}.png"), Color.RED)
        val durationUs = 2_000_000L
        val baseAsset = imageAsset(context.packageName, "base", baseFile)
        val overlayAsset = imageAsset(context.packageName, "overlay", overlayFile)
        val seed = Project.create("multivisual-preview", 320, 180, 30)
        val baseTrack = seed.tracks.first { it.type == TrackType.VISUAL }.copy(
            clips = listOf(imageClip("base-clip", baseAsset.id, durationUs)),
        )
        val audioTrack = seed.tracks.first { it.type == TrackType.AUDIO }
        val overlayTrack = Track(
            id = "visual-overlay-1",
            type = TrackType.VISUAL,
            order = 2,
            collisionPolicy = TrackCollisionPolicy.OVERLAY,
            clips = listOf(imageClip("overlay-clip", overlayAsset.id, durationUs)),
        )
        val project = seed.copy(
            assets = listOf(baseAsset, overlayAsset),
            tracks = listOf(baseTrack, audioTrack, overlayTrack),
        )

        lateinit var controller: Media3PreviewController
        val result = AtomicReference<Result<Unit>>()
        instrumentation.runOnMainSync {
            controller = Media3PreviewController(
                context,
                ProjectCompositionCompiler(AssetUriResolver(context.contentResolver)),
            )
            result.set(controller.load(project))
        }

        try {
            assertTrue(
                "fast preview must accept multiple visual layers: ${result.get().exceptionOrNull()}",
                result.get().isSuccess,
            )
            assertEquals(durationUs, controller.state.value.durationUs)
            assertNull(controller.state.value.errorMessage)
        } finally {
            instrumentation.runOnMainSync { controller.release() }
            baseFile.delete()
            overlayFile.delete()
        }
    }

    private fun createPng(file: File, color: Int): File {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        bitmap.recycle()
        return file
    }

    private fun imageAsset(packageName: String, id: String, file: File): Asset = Asset(
        id = id,
        type = AssetType.IMAGE,
        uri = FileProvider.getUriForFile(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "$packageName.remote-media",
            file,
        ).toString(),
        sourceDurationUs = null,
        sizeBytes = file.length(),
        displayName = file.name,
        mimeType = "image/png",
        fingerprint = "$id-${file.length()}",
    )

    private fun imageClip(id: String, assetId: String, durationUs: Long): Clip = Clip(
        id = id,
        assetId = assetId,
        startUs = 0L,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = durationUs,
    )
}
