package com.localcut.editor.termux

import java.io.File
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

enum class TermuxAnalyzeMediaMode {
    MEDIA,
    TRACKING,
    REVERSE,
}

data class TermuxTrackingOptions(
    val maxFrames: Int = 180,
    val sampleEvery: Int = 2,
    val maxCorners: Int = 120,
) {
    init {
        require(maxFrames in 2..600) { "TERMUX_TRACKING_MAX_FRAMES_INVALID" }
        require(sampleEvery in 1..30) { "TERMUX_TRACKING_SAMPLE_EVERY_INVALID" }
        require(maxCorners in 20..500) { "TERMUX_TRACKING_MAX_CORNERS_INVALID" }
    }
}

data class TermuxReverseOptions(
    val chunkDurationUs: Long = 2_000_000L,
) {
    init {
        require(chunkDurationUs in 250_000L..5_000_000L) { "TERMUX_REVERSE_CHUNK_DURATION_INVALID" }
    }
}

data class TermuxAnalyzeMediaInput(
    val assetId: String,
    val displayName: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceDurationUs: Long?,
    val mode: TermuxAnalyzeMediaMode = TermuxAnalyzeMediaMode.MEDIA,
    val trackingOptions: TermuxTrackingOptions? = null,
    val reverseOptions: TermuxReverseOptions? = null,
) {
    init {
        when (mode) {
            TermuxAnalyzeMediaMode.MEDIA -> {
                require(trackingOptions == null && reverseOptions == null) {
                    "TERMUX_MEDIA_OPTIONS_MODE_INVALID"
                }
            }
            TermuxAnalyzeMediaMode.TRACKING -> {
                require(mimeType.startsWith("video/")) { "TERMUX_TRACKING_REQUIRES_VIDEO" }
                require(reverseOptions == null) { "TERMUX_TRACKING_OPTIONS_MODE_INVALID" }
            }
            TermuxAnalyzeMediaMode.REVERSE -> {
                require(mimeType.startsWith("video/")) { "TERMUX_REVERSE_REQUIRES_VIDEO" }
                require(trackingOptions == null) { "TERMUX_REVERSE_OPTIONS_MODE_INVALID" }
                require(sourceDurationUs != null && sourceDurationUs in 1L..600_000_000L) {
                    "TERMUX_REVERSE_DURATION_INVALID"
                }
            }
        }
    }
}

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

data class TermuxGlobalMotionSample(
    val frameIndex: Int,
    val timeUs: Long,
    val dx: Double,
    val dy: Double,
    val rotationDeg: Double,
    val scale: Double,
    val confidence: Double,
) {
    init {
        require(frameIndex >= 1) { "TERMUX_TRACKING_FRAME_INDEX_INVALID" }
        require(timeUs > 0L) { "TERMUX_TRACKING_TIME_INVALID" }
        require(dx.isFinite() && dy.isFinite() && rotationDeg.isFinite() && scale.isFinite() && confidence.isFinite()) {
            "TERMUX_TRACKING_SAMPLE_NON_FINITE"
        }
        require(scale > 0.0) { "TERMUX_TRACKING_SCALE_INVALID" }
        require(confidence in 0.0..1.0) { "TERMUX_TRACKING_CONFIDENCE_INVALID" }
    }
}

data class TermuxGlobalMotionAnalysis(
    val framesDecoded: Int,
    val frameRate: Double?,
    val samples: List<TermuxGlobalMotionSample>,
) {
    init {
        require(framesDecoded >= 1) { "TERMUX_TRACKING_FRAMES_INVALID" }
        require(frameRate == null || (frameRate.isFinite() && frameRate > 0.0)) { "TERMUX_TRACKING_FPS_INVALID" }
        require(samples.isNotEmpty()) { "TERMUX_TRACKING_SAMPLES_EMPTY" }
        require(samples.zipWithNext().all { (left, right) -> right.timeUs > left.timeUs }) {
            "TERMUX_TRACKING_SAMPLES_UNORDERED"
        }
    }
}

data class TermuxEngineJob(
    val id: String,
    val type: String,
    val status: TermuxEngineJobStatus,
    val progress: Int,
    val errorCode: String? = null,
    val analysis: TermuxMediaAnalysis? = null,
    val analysisMode: String? = null,
    val globalMotion: TermuxGlobalMotionAnalysis? = null,
)

data class TermuxEngineStageResult(
    val job: TermuxEngineJob,
    val sizeBytes: Long,
    val sha256: String,
)

data class TermuxGeneratedMediaMetadata(
    val sizeBytes: Long,
    val sha256: String,
    val mimeType: String,
) {
    init {
        require(sizeBytes > 0L) { "TERMUX_GENERATED_MEDIA_LENGTH_INVALID" }
        require(sha256.matches(Regex("^[0-9a-f]{64}$"))) { "TERMUX_GENERATED_MEDIA_DIGEST_INVALID" }
        require(mimeType == "video/mp4") { "TERMUX_GENERATED_MEDIA_TYPE_INVALID" }
    }
}

data class TermuxGeneratedMediaDownload(
    val job: TermuxEngineJob,
    val media: TermuxGeneratedMediaMetadata,
    val file: File,
)

internal data class TermuxGeneratedMediaHeader(
    val job: TermuxEngineJob,
    val media: TermuxGeneratedMediaMetadata,
    val contentLength: Long,
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
    suspend fun downloadGeneratedMedia(jobId: String, destination: File): Result<TermuxGeneratedMediaDownload> =
        Result.failure(UnsupportedOperationException("TERMUX_GENERATED_MEDIA_DOWNLOAD_UNSUPPORTED"))
}
