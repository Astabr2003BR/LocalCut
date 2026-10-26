package com.localcut.editor.termux

import java.io.InputStream

object TermuxEngineJobCapabilities {
    val REQUIRED: Set<String> = setOf(
        "CREATE_JOB",
        "GET_JOB",
        "CANCEL_JOB",
        "STAGE_MEDIA",
        "ANALYZE_MEDIA",
    )
    const val MAX_RESPONSE_BYTES = 262_144
}

enum class TermuxEngineJobStatus {
    QUEUED,
    RUNNING,
    WAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class TermuxAnalyzeMediaInput(
    val assetId: String,
    val displayName: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceDurationUs: Long?,
)

data class TermuxMediaStream(
    val codecType: String?,
    val codecName: String?,
    val width: Int? = null,
    val height: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val frameRate: Double? = null,
)

data class TermuxMediaAnalysis(
    val formatName: String?,
    val durationUs: Long?,
    val sizeBytes: Long?,
    val bitRate: Long?,
    val streams: List<TermuxMediaStream>,
)

data class TermuxEngineJob(
    val id: String,
    val type: String,
    val status: TermuxEngineJobStatus,
    val progress: Int,
    val errorCode: String? = null,
    val analysis: TermuxMediaAnalysis? = null,
)

data class TermuxEngineStageResult(
    val job: TermuxEngineJob,
    val sizeBytes: Long,
    val sha256: String,
)

class TermuxEngineMediaSource(
    val contentLength: Long,
    private val opener: () -> InputStream,
) {
    init {
        require(contentLength > 0L) { "TERMUX_ENGINE_MEDIA_LENGTH_INVALID" }
    }

    fun openStream(): InputStream = opener()
}

interface TermuxEngineJobClient : TermuxEngineClient {
    suspend fun createAnalyzeMediaJob(input: TermuxAnalyzeMediaInput, idempotencyKey: String): Result<TermuxEngineJob>
    suspend fun getJob(jobId: String): Result<TermuxEngineJob>
    suspend fun cancelJob(jobId: String): Result<TermuxEngineJob>
    suspend fun stageMedia(jobId: String, source: TermuxEngineMediaSource): Result<TermuxEngineStageResult>
}
