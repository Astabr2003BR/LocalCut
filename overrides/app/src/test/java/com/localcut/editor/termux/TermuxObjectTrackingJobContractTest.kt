package com.localcut.editor.termux

import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxObjectTrackingJobContractTest {
    private val testToken = buildString { repeat(43) { append('a') } }

    @Test
    fun encoderKeepsObjectTrackingSeparateAndSerializesNormalizedRoiAndSourceRange() {
        val encoded = TermuxEngineJobRequestEncoder.createAnalyzeMedia(
            requestId = "request_12345678",
            token = testToken,
            key = "object-track-v1:clip-1",
            input = TermuxAnalyzeMediaInput(
                assetId = "asset-1",
                displayName = "clip.mp4",
                mimeType = "video/mp4",
                sizeBytes = 12L,
                sourceDurationUs = 3_000_000L,
                mode = TermuxAnalyzeMediaMode.OBJECT_TRACKING,
                objectTrackingOptions = TermuxObjectTrackingOptions(
                    roi = TermuxObjectTrackingRoi(0.1, 0.2, 0.3, 0.4),
                    sourceInUs = 250_000L,
                    sourceOutUs = 2_500_000L,
                    maxFrames = 240,
                    sampleEvery = 2,
                    maxCorners = 90,
                ),
            ),
        )

        assertTrue(encoded.contains("\"mode\":\"OBJECT_TRACKING\""))
        assertTrue(encoded.contains("\"roi\":{\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.4}"))
        assertTrue(encoded.contains("\"sourceInUs\":250000"))
        assertTrue(encoded.contains("\"sourceOutUs\":2500000"))
        assertTrue(encoded.contains("\"maxFrames\":240"))
        assertTrue(encoded.contains("\"sampleEvery\":2"))
        assertTrue(encoded.contains("\"maxCorners\":90"))
        assertFalse(encoded.contains("\"path\""))
    }

    @Test
    fun parserPreservesTypedObjectPositionResultAndReturnedSourceRange() {
        val sha = "d".repeat(64)
        val parsed = TermuxEngineJobResponseParser.parseStage(
            MiniJson.parse(
                """{"protocolVersion":1,"job":{"id":"123e4567-e89b-12d3-a456-426614174000","type":"ANALYZE_MEDIA","status":"SUCCEEDED","progress":100,"output":{"analysisMode":"opencv-object-position-v1","analysis":{"framesDecoded":12,"frameWidth":1920,"frameHeight":1080,"frameRate":30.0,"sourceInUs":250000,"sourceOutUs":2500000,"samples":[{"frameIndex":3,"timeUs":100000,"dxNorm":0.05,"dyNorm":-0.02,"centerXNorm":0.45,"centerYNorm":0.55,"confidence":0.9}]}},"error":null},"transfer":{"sizeBytes":12,"sha256":"$sha"}}""",
            ).asObject(),
        )

        assertEquals("opencv-object-position-v1", parsed.job.analysisMode)
        assertEquals(null, parsed.job.globalMotion)
        assertEquals(null, parsed.job.analysis)
        val tracking = parsed.job.objectTracking
        assertNotNull(tracking)
        assertEquals(1920, tracking?.frameWidth)
        assertEquals(1080, tracking?.frameHeight)
        assertEquals(250_000L, tracking?.sourceInUs)
        assertEquals(2_500_000L, tracking?.sourceOutUs)
        assertEquals(100_000L, tracking?.samples?.single()?.timeUs)
        assertEquals(0.05, tracking?.samples?.single()?.dxNorm ?: 0.0, 0.0)
        assertEquals(-0.02, tracking?.samples?.single()?.dyNorm ?: 0.0, 0.0)
        assertEquals(0.9, tracking?.samples?.single()?.confidence ?: 0.0, 0.0)
    }

    @Test
    fun roiModeAndSourceRangeFailClosedBeforeNetwork() {
        assertTrue(runCatching { TermuxObjectTrackingRoi(0.9, 0.1, 0.2, 0.2) }.isFailure)
        assertTrue(runCatching {
            TermuxObjectTrackingOptions(
                roi = TermuxObjectTrackingRoi(0.1, 0.1, 0.2, 0.2),
                sourceInUs = 500_000L,
                sourceOutUs = 500_000L,
            )
        }.isFailure)
        assertTrue(runCatching {
            TermuxAnalyzeMediaInput(
                assetId = "asset",
                displayName = null,
                mimeType = "video/mp4",
                sizeBytes = 1L,
                sourceDurationUs = 1_000_000L,
                mode = TermuxAnalyzeMediaMode.OBJECT_TRACKING,
                objectTrackingOptions = null,
            )
        }.isFailure)
        assertTrue(runCatching {
            TermuxAnalyzeMediaInput(
                assetId = "asset",
                displayName = null,
                mimeType = "video/mp4",
                sizeBytes = 1L,
                sourceDurationUs = 1_000_000L,
                mode = TermuxAnalyzeMediaMode.OBJECT_TRACKING,
                objectTrackingOptions = TermuxObjectTrackingOptions(
                    roi = TermuxObjectTrackingRoi(0.1, 0.1, 0.2, 0.2),
                    sourceInUs = 250_000L,
                    sourceOutUs = 1_250_000L,
                ),
            )
        }.isFailure)
    }
}
