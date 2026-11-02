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
