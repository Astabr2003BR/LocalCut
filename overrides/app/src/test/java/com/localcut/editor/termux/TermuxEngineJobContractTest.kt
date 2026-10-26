package com.localcut.editor.termux

import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEngineJobContractTest {
    private val token = "a".repeat(43)

    @Test
    fun jobsAreOptionalAndDoNotChangeCoreCapabilities() {
        assertEquals(
            setOf("PING", "GET_STATUS", "GET_CAPABILITIES"),
            TermuxEngineProtocol.REQUIRED_CAPABILITIES,
        )
        assertTrue(TermuxEngineJobCapabilities.REQUIRED.contains("ANALYZE_MEDIA"))
        assertFalse(TermuxEngineProtocol.REQUIRED_CAPABILITIES.contains("ANALYZE_MEDIA"))
    }

    @Test
    fun analyzeMediaEncoderUsesTypedMetadataAndNeverAPath() {
        val encoded = TermuxEngineJobRequestEncoder.createAnalyzeMedia(
            "request_12345678",
            token,
            "analyze-media-v1:abc123",
            TermuxAnalyzeMediaInput(
                "asset-1",
                "vídeo teste.mp4",
                "video/mp4",
                5L,
                2_000_000L,
            ),
        )
        assertTrue(encoded.contains("\"operation\":\"CREATE_JOB\""))
        assertTrue(encoded.contains("\"jobType\":\"ANALYZE_MEDIA\""))
        assertTrue(encoded.contains("\"mode\":\"MEDIA\""))
        assertTrue(encoded.contains("vídeo teste.mp4"))
        assertFalse(encoded.contains("\"path\""))
    }

    @Test
    fun stageParserReturnsAnalysisAndDigest() {
        val sha = "b".repeat(64)
        val parsed = TermuxEngineJobResponseParser.parseStage(
            MiniJson.parse(
                """{"protocolVersion":1,"job":{"id":"123e4567-e89b-12d3-a456-426614174000","type":"ANALYZE_MEDIA","status":"SUCCEEDED","progress":100,"output":{"analysis":{"formatName":"mov,mp4","durationUs":2000000,"sizeBytes":5,"bitRate":64000,"streams":[{"codecType":"video","codecName":"h264","width":1920,"height":1080,"frameRate":30.0}]}},"error":null},"transfer":{"sizeBytes":5,"sha256":"$sha"}}""",
            ).asObject(),
        )
        assertEquals(TermuxEngineJobStatus.SUCCEEDED, parsed.job.status)
        assertEquals(2_000_000L, parsed.job.analysis?.durationUs)
        assertEquals(1920, parsed.job.analysis?.streams?.single()?.width)
        assertEquals(sha, parsed.sha256)
    }
}
