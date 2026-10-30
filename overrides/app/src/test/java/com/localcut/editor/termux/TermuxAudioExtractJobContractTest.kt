package com.localcut.editor.termux

import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxAudioExtractJobContractTest {
    private val token = "a".repeat(43)

    @Test
    fun extractAudioUsesTypedAnalyzeModeAndAcceptsOnlyGeneratedAudioMime() {
        val encoded = TermuxEngineJobRequestEncoder.createAnalyzeMedia(
            "request_12345678",
            token,
            "extract-audio-v1:abc123",
            TermuxAnalyzeMediaInput(
                assetId = "asset-video",
                displayName = "clip.mp4",
                mimeType = "video/mp4",
                sizeBytes = 5L,
                sourceDurationUs = 2_000_000L,
                mode = TermuxAnalyzeMediaMode.EXTRACT_AUDIO,
            ),
        )

        assertTrue(encoded.contains("\"jobType\":\"ANALYZE_MEDIA\""))
        assertTrue(encoded.contains("\"mode\":\"EXTRACT_AUDIO\""))
        TermuxGeneratedMediaMetadata(
            sizeBytes = 5L,
            sha256 = "b".repeat(64),
            mimeType = "audio/mp4",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractAudioRejectsNonVideoInput() {
        TermuxAnalyzeMediaInput(
            assetId = "asset-audio",
            displayName = "clip.m4a",
            mimeType = "audio/mp4",
            sizeBytes = 5L,
            sourceDurationUs = 2_000_000L,
            mode = TermuxAnalyzeMediaMode.EXTRACT_AUDIO,
        )
    }
}
