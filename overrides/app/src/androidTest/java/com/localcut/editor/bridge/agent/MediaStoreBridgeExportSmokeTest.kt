package com.localcut.editor.bridge.agent

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localcut.editor.domain.model.Project
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreBridgeExportSmokeTest {
    @Test
    fun commitsPendingVideoIntoMoviesLocalCutThenPublishesIt() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val project = Project.create("Bridge Smoke", 1080, 1920, 30)
        val factory = AndroidMediaStoreBridgeExportTargetFactory(
            contentResolver = resolver,
            sdkInt = Build.VERSION.SDK_INT,
            nowEpochMs = { 1_797_000_123_456L },
        )
        val target = factory.create(project).getOrThrow()
        val displayName = Regex("\\\"displayName\\\":\\\"([^\\\"]+)\\\"")
            .find(target.resultJson)?.groupValues?.get(1)
        assertNotNull(displayName)

        val temp = File.createTempFile("bridge-mediastore-smoke-", ".mp4", context.cacheDir)
        val bytes = ByteArray(1024) { index -> (index % 251).toByte() }
        temp.writeBytes(bytes)

        var insertedId: Long? = null
        try {
            target.destination.commitFrom(temp).getOrThrow()

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.query(
                collection,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.RELATIVE_PATH,
                    MediaStore.Video.Media.IS_PENDING,
                    MediaStore.Video.Media.SIZE,
                ),
                "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                arrayOf(displayName!!),
                null,
            )!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                insertedId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                assertEquals("Movies/LocalCut/", cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.IS_PENDING)))
                assertEquals(bytes.size.toLong(), cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)))
            }
        } finally {
            insertedId?.let { id ->
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            }
            temp.delete()
        }
    }
}
