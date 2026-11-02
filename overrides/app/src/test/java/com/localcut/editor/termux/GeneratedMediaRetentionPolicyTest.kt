package com.localcut.editor.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedMediaRetentionPolicyTest {
    private val jobId = "123e4567-e89b-12d3-a456-426614174000"
    private val outputId = "223e4567-e89b-12d3-a456-426614174001"
    private val fileName = "reverse-$jobId-$outputId.mp4"
    private val authority = "com.localcut.editor.remote-media"

    @Test
    fun onlyStrictReverseUuidNamespaceIsCollectable() {
        assertTrue(GeneratedMediaRetentionPolicy.isGeneratedReverseName(fileName))
        assertFalse(GeneratedMediaRetentionPolicy.isGeneratedReverseName("shared-$outputId.mp4"))
        assertFalse(GeneratedMediaRetentionPolicy.isGeneratedReverseName("reverse-not-a-job-$outputId.mp4"))
        assertFalse(GeneratedMediaRetentionPolicy.isGeneratedReverseName("$fileName.part"))
        assertFalse(GeneratedMediaRetentionPolicy.isGeneratedReverseName("../$fileName"))
    }

    @Test
    fun providerReferenceProtectsOnlyExactAuthorityAndPath() {
        val referenced = GeneratedMediaRetentionPolicy.referencedGeneratedFileNames(
            assetUris = listOf(
                "content://$authority/remote-media/$fileName",
                "content://evil.example/remote-media/reverse-323e4567-e89b-12d3-a456-426614174002-423e4567-e89b-12d3-a456-426614174003.mp4",
                "content://$authority/not-remote-media/reverse-523e4567-e89b-12d3-a456-426614174004-623e4567-e89b-12d3-a456-426614174005.mp4",
                "not a uri",
            ),
            providerAuthority = authority,
        )

        assertEquals(setOf(fileName), referenced)
    }

    @Test
    fun undoOrRedoAvailabilityBlocksCollectionBeforeAgeOrReferenceChecks() {
        val old = 1_000L
        val now = old + GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS + 1L

        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = fileName,
                lastModifiedMs = old,
                nowMs = now,
                protectedFileNames = emptySet(),
                canUndo = true,
                canRedo = false,
            ),
        )
        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = fileName,
                lastModifiedMs = old,
                nowMs = now,
                protectedFileNames = emptySet(),
                canUndo = false,
                canRedo = true,
            ),
        )
    }

    @Test
    fun referencedOrRecentGeneratedFileIsProtectedAndOldOrphanIsCollectable() {
        val now = 10L * GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS
        val old = now - GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS - 1L
        val recentBoundary = now - GeneratedMediaRetentionPolicy.DEFAULT_GRACE_PERIOD_MS

        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = fileName,
                lastModifiedMs = old,
                nowMs = now,
                protectedFileNames = setOf(fileName),
                canUndo = false,
                canRedo = false,
            ),
        )
        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = fileName,
                lastModifiedMs = recentBoundary,
                nowMs = now,
                protectedFileNames = emptySet(),
                canUndo = false,
                canRedo = false,
            ),
        )
        assertTrue(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = fileName,
                lastModifiedMs = old,
                nowMs = now,
                protectedFileNames = emptySet(),
                canUndo = false,
                canRedo = false,
            ),
        )
    }

    @Test
    fun impossibleOrFutureTimestampsFailClosed() {
        val now = 1_000_000L
        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = fileName,
                lastModifiedMs = 0L,
                nowMs = now,
                protectedFileNames = emptySet(),
                canUndo = false,
                canRedo = false,
            ),
        )
        assertFalse(
            GeneratedMediaRetentionPolicy.shouldCollect(
                fileName = fileName,
                lastModifiedMs = now + 1L,
                nowMs = now,
                protectedFileNames = emptySet(),
                canUndo = false,
                canRedo = false,
            ),
        )
    }
}
