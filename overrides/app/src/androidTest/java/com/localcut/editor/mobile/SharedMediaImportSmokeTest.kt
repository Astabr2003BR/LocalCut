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
import com.localcut.editor.domain.model.TrackType
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMediaImportSmokeTest {
    @Test
    fun importsOneSharedImageOnColdStart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runtime = LocalCutProcessRuntime.get(context)
        val before = visualClipCount(runtime)
        val uri = createPngFixture()

        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        ) as MainActivity

        try {
            waitForVisualClipCount(runtime, before + 1)
            assertEquals(before + 1, visualClipCount(runtime))
        } finally {
            activity.finish()
        }
    }

    @Test
    fun warmActivityImportsOnlyTheNewShareOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runtime = LocalCutProcessRuntime.get(context)
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        val before = visualClipCount(runtime)
        val uri = createPngFixture()

        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                },
            )
            instrumentation.waitForIdleSync()
            waitForVisualClipCount(runtime, before + 1)
            SystemClock.sleep(250)
            assertEquals(before + 1, visualClipCount(runtime))
        } finally {
            activity.finish()
        }
    }

    private fun visualClipCount(runtime: LocalCutProcessRuntime): Int =
        runtime.session.state.project.tracks
            .filter { it.type == TrackType.VISUAL }
            .sumOf { it.clips.size }

    private fun waitForVisualClipCount(runtime: LocalCutProcessRuntime, expected: Int) {
        repeat(50) {
            if (visualClipCount(runtime) == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(expected, visualClipCount(runtime))
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
