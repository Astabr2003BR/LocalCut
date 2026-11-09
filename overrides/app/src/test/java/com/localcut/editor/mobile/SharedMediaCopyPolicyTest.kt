package com.localcut.editor.mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaCopyPolicyTest {
    @Test
    fun imageAndVoiceLimitsMatchRemoteMediaValidator() {
        assertEquals(RemoteMediaValidator.limitBytes(RemoteMediaKind.IMAGE), SharedMediaCopyPolicy.limitForMime("image/jpeg"))
        assertEquals(RemoteMediaValidator.limitBytes(RemoteMediaKind.AUDIO), SharedMediaCopyPolicy.limitForMime("audio/mpeg"))
        assertEquals(RemoteMediaValidator.limitBytes(RemoteMediaKind.VIDEO), SharedMediaCopyPolicy.limitForMime("video/mp4"))
        assertEquals(RemoteMediaValidator.limitBytes(RemoteMediaKind.IMAGE), SharedMediaCopyPolicy.limitForMime(" IMAGE/PNG; charset=binary "))
        assertEquals("SHARED_MEDIA_TYPE_UNSUPPORTED",
            assertThrows(IllegalArgumentException::class.java) { SharedMediaCopyPolicy.limitForMime("application/pdf") }.message)
    }

    @Test
    fun copiesValidImageOrAudioWithExactBytes() {
        val source = ByteArray(128 * 1024) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val count = SharedMediaCopyPolicy.copy(ByteArrayInputStream(source), output, source.size.toLong())
        assertEquals(source.size.toLong(), count)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun rejectsLargerInputEvenWhenSizeWasUnknownAndDoesNotWritePastLimit() {
        val input = ByteArrayInputStream(ByteArray(65 * 1024))
        val output = ByteArrayOutputStream()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            SharedMediaCopyPolicy.copy(input, output, 64L * 1024L)
        }
        assertEquals("SHARED_MEDIA_TOO_LARGE", exception.message)
        assertTrue(output.size() <= 64 * 1024)
    }

    @Test
    fun rejectsEmptyInput() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            SharedMediaCopyPolicy.copy(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 2048)
        }
        assertEquals("SHARED_MEDIA_EMPTY", exception.message)
    }

    @Test
    fun propagatesInterruptedSourceAndLetsStagerDeletePartialFile() {
        val source = object : InputStream() {
            private var reads = 0
            override fun read(): Int {
                if (reads++ > 3) throw IOException("provider disconnected")
                return 1
            }
        }
        assertThrows(IOException::class.java) {
            SharedMediaCopyPolicy.copy(source, ByteArrayOutputStream(), 2048)
        }
    }

    @Test
    fun rejectsInvalidLimitAndBrokenZeroLengthRead() {
        assertEquals("SHARED_MEDIA_LIMIT_INVALID",
            assertThrows(IllegalArgumentException::class.java) {
                SharedMediaCopyPolicy.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 0)
            }.message)
        val broken = object : InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertEquals("SHARED_MEDIA_READ_STALLED",
            assertThrows(IllegalArgumentException::class.java) {
                SharedMediaCopyPolicy.copy(broken, ByteArrayOutputStream(), 2048)
            }.message)
    }
}
