package com.localcut.editor.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAudioRetentionPolicyTest {
    private val jobId = "123e4567-e89b-12d3-a456-426614174000"
    private val fileId = "223e4567-e89b-12d3-a456-426614174000"
    private val audioName = "audio-$jobId-$fileId.m4a"

    @Test
    fun generatedAudioUriIsProtectedWhileReferencedByProject() {
        val authority = "com.localcut.editor.remote-media"
        val protected = GeneratedMediaRetentionPolicy.referencedGeneratedFileNames(
            assetUris = listOf("content://$authority/remote-media/$audioName"),
            providerAuthority = authority,
        )

        assertEquals(setOf(audioName), protected)
        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = audioName,
                lastModifiedMs = 1_000L,
                nowMs = 100_000L,
                protectedFileNames = protected,
                canUndo = false,
                canRedo = false,
                gracePeriodMs = 10_000L,
            ),
        )
    }

    @Test
    fun oldUnreferencedGeneratedAudioIsCollectibleOnlyWithoutHistory() {
        assertTrue(GeneratedMediaRetentionPolicy.isGeneratedAudioName(audioName))
        assertTrue(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = audioName,
                lastModifiedMs = 1_000L,
                nowMs = 100_000L,
                protectedFileNames = emptySet(),
                canUndo = false,
                canRedo = false,
                gracePeriodMs = 10_000L,
            ),
        )
        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = audioName,
                lastModifiedMs = 1_000L,
                nowMs = 100_000L,
                protectedFileNames = emptySet(),
                canUndo = true,
                canRedo = false,
                gracePeriodMs = 10_000L,
            ),
        )
        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = "recording.m4a",
                lastModifiedMs = 1_000L,
                nowMs = 100_000L,
                protectedFileNames = emptySet(),
                canUndo = false,
                canRedo = false,
                gracePeriodMs = 10_000L,
            ),
        )
    }
}
