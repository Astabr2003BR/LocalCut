package com.localcut.editor.mobile

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TikTokShareIntentFactoryTest {
    @Suppress("DEPRECATION")
    @Test fun sendsAllImagesInChosenOrderWithReadOnlyGrants() {
        val pages = listOf("3", "1", "2").map { Uri.parse("content://localcut.test/images/$it") }
        val intent = TikTokShareIntentFactory.create(pages)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("image/*", intent.type)
        assertEquals(pages, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
        val clip = requireNotNull(intent.clipData)
        assertEquals(pages, (0 until clip.itemCount).map { clip.getItemAt(it).uri })
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test fun rejectsEmptySingleOversizedAndFileUris() {
        val valid = Uri.parse("content://localcut.test/images/1")
        assertThrows(IllegalArgumentException::class.java) { TikTokShareIntentFactory.create(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { TikTokShareIntentFactory.create(listOf(valid)) }
        assertThrows(IllegalArgumentException::class.java) { TikTokShareIntentFactory.create(List(36) { valid }) }
        assertThrows(IllegalArgumentException::class.java) { TikTokShareIntentFactory.create(listOf(valid, Uri.parse("file:///private/photo.png"))) }
    }

    @Test fun supportsMaximumBatch() {
        val pages = (1..35).map { Uri.parse("content://localcut.test/images/$it") }
        assertEquals(35, TikTokShareIntentFactory.create(pages).clipData?.itemCount)
    }
}
