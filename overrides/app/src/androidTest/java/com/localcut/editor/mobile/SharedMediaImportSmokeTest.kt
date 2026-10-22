package com.localcut.editor.mobile

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.MainActivity
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineMath
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMediaImportSmokeTest {
    @Test
    fun importsColdAndWarmSharedImagesExactlyOnceAndRetainsPrivateCopies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var runtime: LocalCutProcessRuntime
        instrumentation.runOnMainSync {
            runtime = LocalCutProcessRuntime.get(context)
        }

        val before = visualClipCount(runtime)
        val beforeAssetIds = runtime.session.state.project.assets.map { it.id }.toSet()
        val coldUri = createPngFixture()
        val warmUri = createPngFixture()

        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, coldUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        ) as MainActivity

        waitForVisualClipCount(runtime, before + 1)
        assertEquals(before + 1, visualClipCount(runtime))
        assertFalse(activity.isFinishing)

        val coldAsset = newAssetsSince(runtime, beforeAssetIds).single()
        assertNotEquals(
            "shared media must not keep the transient source URI",
            coldUri.toString(),
            coldAsset.uri,
        )
        assertEquals(1, context.contentResolver.delete(coldUri, null, null))
        assertTrue(
            "cold shared media must still resolve after deleting the source fixture",
            AssetUriResolver(context.contentResolver).resolve(coldAsset).isSuccess,
        )
        waitForPreviewToMatchProject(runtime)

        val afterColdAssetIds = runtime.session.state.project.assets.map { it.id }.toSet()
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, warmUri)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            },
        )
        instrumentation.waitForIdleSync()
        waitForVisualClipCount(runtime, before + 2)
        SystemClock.sleep(250)

        assertEquals(before + 2, visualClipCount(runtime))
        assertFalse(activity.isFinishing)

        val warmAsset = newAssetsSince(runtime, afterColdAssetIds).single()
        assertNotEquals(
            "warm shared media must not keep the transient source URI",
            warmUri.toString(),
            warmAsset.uri,
        )
        assertEquals(1, context.contentResolver.delete(warmUri, null, null))
        assertTrue(
            "warm shared media must still resolve after deleting the source fixture",
            AssetUriResolver(context.contentResolver).resolve(warmAsset).isSuccess,
        )
        waitForPreviewToMatchProject(runtime)
    }

    private fun visualClipCount(runtime: LocalCutProcessRuntime): Int =
        runtime.session.state.project.tracks
            .filter { it.type == TrackType.VISUAL }
            .sumOf { it.clips.size }

    private fun newAssetsSince(runtime: LocalCutProcessRuntime, ids: Set<String>): List<Asset> =
        runtime.session.state.project.assets.filter { it.id !in ids }

    private fun waitForVisualClipCount(runtime: LocalCutProcessRuntime, expected: Int) {
        repeat(50) {
            if (visualClipCount(runtime) == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(expected, visualClipCount(runtime))
    }

    private fun waitForPreviewToMatchProject(runtime: LocalCutProcessRuntime) {
        repeat(50) {
            val expected = TimelineMath.durationUs(runtime.session.state.project)
            val actual = runtime.previewController.state.value.durationUs
            if (expected > 0L && actual == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(
            TimelineMath.durationUs(runtime.session.state.project),
            runtime.previewController.state.value.durationUs,
        )
    }

    private fun createPngFixture(): Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "localcut-share-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "failed to create MediaStore fixture" }

        resolver.openOutputStream(uri)?.use { output ->
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            try {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            } finally {
                bitmap.recycle()
            }
        } ?: error("failed to open MediaStore fixture")

        return uri
    }
}
