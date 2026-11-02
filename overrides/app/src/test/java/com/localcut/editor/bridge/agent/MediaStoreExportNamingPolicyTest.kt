package com.localcut.editor.bridge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreExportNamingPolicyTest {
    @Test
    fun createsSanitizedMp4NameAndMoviesLocalCutPath() {
        val spec = MediaStoreExportNamingPolicy.create(
            projectTitle = "Meu Anime: Episódio/01?",
            nowEpochMs = 1_797_000_000_000L,
        )

        assertTrue(spec.displayName.endsWith(".mp4"))
        assertTrue(spec.displayName.startsWith("LocalCut-"))
        assertTrue(spec.displayName.none { it == '/' || it == '?' || it == ':' })
        assertEquals("Movies/LocalCut", spec.relativePath)
        assertEquals("video/mp4", spec.mimeType)
    }

    @Test
    fun blankProjectTitleStillProducesStableSafeName() {
        val spec = MediaStoreExportNamingPolicy.create("   ", 1234L)
        assertEquals("LocalCut-1234.mp4", spec.displayName)
    }
}
