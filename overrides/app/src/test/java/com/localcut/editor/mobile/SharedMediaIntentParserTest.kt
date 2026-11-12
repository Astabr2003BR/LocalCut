package com.localcut.editor.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaIntentParserTest {
    @Test
    fun parsesSingleImageShare() {
        val result = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND",
            mimeType = "image/jpeg",
            uriStrings = listOf("content://media/a"),
        ).getOrThrow()

        assertEquals("image/jpeg", result.mimeType)
        assertEquals(listOf("content://media/a"), result.uriStrings)
    }

    @Test
    fun preservesMultipleShareOrder() {
        val result = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "video/mp4",
            uriStrings = listOf("content://media/a", "content://media/b", "content://media/c"),
        ).getOrThrow()

        assertEquals(
            listOf("content://media/a", "content://media/b", "content://media/c"),
            result.uriStrings,
        )
    }

    @Test
    fun parsesMixedImageAndAudioFromAndroidFiles() {
        val mime = mapOf(
            "content://media/portrait" to "image/jpeg",
            "content://media/voice" to "audio/mpeg",
        )
        val result = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "*/*",
            uriStrings = listOf("content://media/portrait", "content://media/voice"),
            resolveUriMime = { mime[it] },
        ).getOrThrow()

        assertEquals("*/*", result.mimeType)
        assertEquals(listOf("content://media/portrait", "content://media/voice"), result.uriStrings)
    }

    @Test
    fun mixedShareFailsClosedOnUnsupportedFile() {
        val result = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "*/*",
            uriStrings = listOf("content://media/portrait", "content://docs/report"),
            resolveUriMime = { if (it.endsWith("/portrait")) "image/jpeg" else "application/pdf" },
        )
        assertTrue(result.isFailure)
        assertEquals("SHARED_MEDIA_MIXED_TYPE_UNSUPPORTED", result.exceptionOrNull()?.message)
    }

    @Test
    fun mixedShareRejectsDuplicateOrNonContentUris() {
        val duplicated = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "*/*",
            uriStrings = listOf("content://media/a", "content://media/a"),
            resolveUriMime = { "image/png" },
        )
        assertTrue(duplicated.isFailure)
        val unsafe = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "*/*",
            uriStrings = listOf("content://media/a", "file:///sdcard/secret"),
            resolveUriMime = { "image/png" },
        )
        assertTrue(unsafe.isFailure)
    }

    @Test
    fun mixedShareRejectsOversizedBatchAndSingleWildcardShare() {
        val batch = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "*/*",
            uriStrings = (0..8).map { "content://media/" + it },
            resolveUriMime = { "image/png" },
        )
        assertTrue(batch.isFailure)

        val single = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND",
            mimeType = "*/*",
            uriStrings = listOf("content://media/a"),
            resolveUriMime = { "image/png" },
        )
        assertTrue(single.isFailure)
    }

    @Test
    fun mixedShareRejectsMissingPerUriMime() {
        val result = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND_MULTIPLE",
            mimeType = "*/*",
            uriStrings = listOf("content://media/a", "content://media/b"),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsFileScheme() {
        val result = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND",
            mimeType = "video/mp4",
            uriStrings = listOf("file:///sdcard/x.mp4"),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsMissingStream() {
        val result = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND",
            mimeType = "image/png",
            uriStrings = emptyList(),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsUnsupportedMime() {
        val result = SharedMediaIntentParser.parse(
            action = "android.intent.action.SEND",
            mimeType = "application/pdf",
            uriStrings = listOf("content://docs/a"),
        )

        assertTrue(result.isFailure)
    }
}
