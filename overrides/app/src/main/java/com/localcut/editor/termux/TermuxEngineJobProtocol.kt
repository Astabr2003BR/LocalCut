package com.localcut.editor.termux

import com.localcut.editor.data.project.JNull
import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asBoolean
import com.localcut.editor.data.project.asDouble
import com.localcut.editor.data.project.asInt
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required
import kotlin.math.roundToLong

internal object TermuxEngineJobRequestEncoder {
    private val REQUEST_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
    private val IDEMPOTENCY_KEY = Regex("^[A-Za-z0-9._:-]{1,160}$")
    private val JOB_ID = Regex("^[0-9a-fA-F-]{36}$")

    fun createAnalyzeMedia(requestId: String, token: String, key: String, input: TermuxAnalyzeMediaInput): String {
        validateCommon(requestId, token)
        require(IDEMPOTENCY_KEY.matches(key)) { "TERMUX_ENGINE_IDEMPOTENCY_KEY_INVALID" }
        require(input.assetId.isNotBlank() && input.assetId.length <= 160) { "TERMUX_ENGINE_ASSET_ID_INVALID" }
        require(input.mimeType.isNotBlank() && input.mimeType.length <= 160) { "TERMUX_ENGINE_MEDIA_TYPE_INVALID" }
        require(input.sizeBytes > 0L) { "TERMUX_ENGINE_MEDIA_LENGTH_INVALID" }
        require(input.displayName == null || input.displayName.length <= 512) { "TERMUX_ENGINE_DISPLAY_NAME_INVALID" }
        require(input.sourceDurationUs == null || input.sourceDurationUs >= 0L) { "TERMUX_ENGINE_MEDIA_DURATION_INVALID" }
        val tracking = if (input.mode == TermuxAnalyzeMediaMode.TRACKING) {
            input.trackingOptions ?: TermuxTrackingOptions()
        } else {
            null
        }
        val objectTracking = if (input.mode == TermuxAnalyzeMediaMode.OBJECT_TRACKING) {
            requireNotNull(input.objectTrackingOptions)
        } else {
            null
        }
        val reverse = if (input.mode == TermuxAnalyzeMediaMode.REVERSE) {
            input.reverseOptions ?: TermuxReverseOptions()
        } else {
            null
        }
        return buildString {
            append('{'); appendCommon(requestId, token, "CREATE_JOB")
            append(",\"jobType\":\"ANALYZE_MEDIA\",\"idempotencyKey\":"); append(jsonString(key))
            append(",\"input\":{\"mode\":"); append(jsonString(input.mode.name))
            append(",\"assetId\":"); append(jsonString(input.assetId))
            input.displayName?.let { append(",\"displayName\":"); append(jsonString(it)) }
            append(",\"mimeType\":"); append(jsonString(input.mimeType))
            append(",\"sizeBytes\":"); append(input.sizeBytes)
            input.sourceDurationUs?.let { append(",\"sourceDurationUs\":"); append(it) }
            tracking?.let {
                append(",\"maxFrames\":"); append(it.maxFrames)
                append(",\"sampleEvery\":"); append(it.sampleEvery)
                append(",\"maxCorners\":"); append(it.maxCorners)
            }
            objectTracking?.let {
                append(",\"sourceInUs\":"); append(it.sourceInUs)
                it.sourceOutUs?.let { sourceOutUs ->
                    append(",\"sourceOutUs\":"); append(sourceOutUs)
                }
                append(",\"maxFrames\":"); append(it.maxFrames)
                append(",\"sampleEvery\":"); append(it.sampleEvery)
                append(",\"maxCorners\":"); append(it.maxCorners)
                append(",\"roi\":{")
                append("\"x\":"); append(it.roi.x); append(',')
                append("\"y\":"); append(it.roi.y); append(',')
                append("\"width\":"); append(it.roi.width); append(',')
                append("\"height\":"); append(it.roi.height); append('}')
            }
            reverse?.let {
                append(",\"chunkDurationUs\":"); append(it.chunkDurationUs)
            }
            append("}}\n")
        }
    }

    fun getJob(requestId: String, token: String, jobId: String) = jobRequest(requestId, token, "GET_JOB", jobId)

    fun getGeneratedMedia(requestId: String, token: String, jobId: String): String {
        validateCommon(requestId, token); validateJobId(jobId)
        return buildString {
            append('{'); appendCommon(requestId, token, "GET_JOB")
            append(",\"jobId\":"); append(jsonString(jobId.lowercase()))
            append(",\"includeGeneratedMedia\":true}\n")
        }
    }

    fun cancelJob(requestId: String, token: String, jobId: String) = jobRequest(requestId, token, "CANCEL_JOB", jobId)

    fun stageMedia(requestId: String, token: String, jobId: String, contentLength: Long): String {
        validateCommon(requestId, token); validateJobId(jobId)
        require(contentLength > 0L) { "TERMUX_ENGINE_MEDIA_LENGTH_INVALID" }
        return buildString {
            append('{'); appendCommon(requestId, token, "STAGE_MEDIA")
            append(",\"jobId\":"); append(jsonString(jobId.lowercase()))
            append(",\"contentLength\":"); append(contentLength); append("}\n")
        }
    }

    private fun jobRequest(requestId: String, token: String, operation: String, jobId: String): String {
        validateCommon(requestId, token); validateJobId(jobId)
        return buildString {
            append('{'); appendCommon(requestId, token, operation)
            append(",\"jobId\":"); append(jsonString(jobId.lowercase())); append("}\n")
        }
    }

    private fun validateCommon(requestId: String, token: String) {
        require(REQUEST_ID.matches(requestId)) { "TERMUX_ENGINE_REQUEST_ID_INVALID" }
        TermuxEngineTokenPolicy.normalize(token)
    }

    private fun validateJobId(jobId: String) = require(JOB_ID.matches(jobId)) { "TERMUX_ENGINE_JOB_ID_INVALID" }

    private fun StringBuilder.appendCommon(requestId: String, token: String, operation: String) {
        append("\"protocolVersion\":"); append(TermuxEngineProtocol.PROTOCOL_VERSION)
        append(",\"projectId\":"); append(jsonString(TermuxEngineProtocol.PROJECT_ID))
        append(",\"requestId\":"); append(jsonString(requestId))
        append(",\"token\":"); append(jsonString(TermuxEngineTokenPolicy.normalize(token)))
        append(",\"operation\":"); append(jsonString(operation))
    }
}

internal object TermuxEngineJobResponseParser {
    private const val GLOBAL_MOTION_MODE = "opencv-global-motion-v1"
    private const val OBJECT_TRACKING_MODE = "opencv-object-position-v1"

    fun parseJob(root: JObject): TermuxEngineJob {
        requireProtocol(root)
        return parseJobObject(root.required("job").asObject())
    }

    fun parseStage(root: JObject): TermuxEngineStageResult {
        val transfer = root.required("transfer").asObject()
        val sha = transfer.required("sha256").asString().lowercase()
        require(sha.matches(Regex("^[0-9a-f]{64}$"))) { "TERMUX_ENGINE_TRANSFER_DIGEST_INVALID" }
        return TermuxEngineStageResult(
            job = parseJob(root),
            sizeBytes = transfer.required("sizeBytes").asLong().also { require(it > 0L) },
            sha256 = sha,
        )
    }

    fun parseGeneratedMediaHeader(root: JObject): TermuxGeneratedMediaHeader {
        requireProtocol(root)
        require(root.required("binaryBody").asBoolean()) { "TERMUX_GENERATED_MEDIA_BODY_MISSING" }
        val mediaObject = root.required("media").asObject()
        val media = TermuxGeneratedMediaMetadata(
            sizeBytes = mediaObject.required("sizeBytes").asLong(),
            sha256 = mediaObject.required("sha256").asString().lowercase(),
            mimeType = mediaObject.required("mimeType").asString(),
        )
        val contentLength = root.required("contentLength").asLong()
        val transportSha = root.required("sha256").asString().lowercase()
        require(contentLength == media.sizeBytes) { "TERMUX_GENERATED_MEDIA_LENGTH_MISMATCH" }
        require(transportSha == media.sha256) { "TERMUX_GENERATED_MEDIA_DIGEST_MISMATCH" }
        return TermuxGeneratedMediaHeader(
            job = parseJob(root),
            media = media,
            contentLength = contentLength,
            sha256 = transportSha,
        )
    }

    private fun requireProtocol(root: JObject) {
        val protocol = root.required("protocolVersion").asInt()
        require(protocol == TermuxEngineProtocol.PROTOCOL_VERSION) {
            "TERMUX_ENGINE_PROTOCOL_MISMATCH"
        }
    }

    private fun parseJobObject(job: JObject): TermuxEngineJob {
        val progress = job.required("progress").asInt().also { require(it in 0..100) }
        val errorCode = (job.optional("error") as? JObject)?.optional("code")?.let { runCatching { it.asString() }.getOrNull() }
        val output = job.optional("output") as? JObject
        val analysisMode = output?.optional("analysisMode")?.let { value ->
            if (value is JNull) null else runCatching { value.asString() }.getOrNull()
        }
        val analysisObject = output?.optional("analysis") as? JObject
        val globalMotion = if (analysisMode == GLOBAL_MOTION_MODE && analysisObject != null) {
            parseGlobalMotion(analysisObject)
        } else {
            null
        }
        val objectTracking = if (analysisMode == OBJECT_TRACKING_MODE && analysisObject != null) {
            parseObjectTracking(analysisObject)
        } else {
            null
        }
        val analysis = if (analysisObject != null && globalMotion == null && objectTracking == null) {
            parseAnalysis(analysisObject)
        } else {
            null
        }
        return TermuxEngineJob(
            id = job.required("id").asString(),
            type = job.required("type").asString(),
            status = TermuxEngineJobStatus.valueOf(job.required("status").asString()),
            progress = progress,
            errorCode = errorCode,
            analysis = analysis,
            analysisMode = analysisMode,
            globalMotion = globalMotion,
            objectTracking = objectTracking,
        )
    }

    private fun parseGlobalMotion(root: JObject): TermuxGlobalMotionAnalysis {
        val framesDecoded = root.required("framesDecoded").asInt()
        val frameRate = root.optional("frameRate")?.let { value ->
            if (value is JNull) null else value.asDouble()
        }
        val samples = root.required("samples").asArray().values.map { value ->
            val sample = value.asObject()
            val frameIndex = sample.required("frameIndex").asInt()
            val timeUs = sample.optional("timeUs")?.let { candidate ->
                if (candidate is JNull) null else candidate.asLong()
            } ?: run {
                val fps = requireNotNull(frameRate) { "TERMUX_TRACKING_TIME_OR_FPS_REQUIRED" }
                (frameIndex.toDouble() * 1_000_000.0 / fps).roundToLong()
            }
            TermuxGlobalMotionSample(
                frameIndex = frameIndex,
                timeUs = timeUs,
                dx = sample.required("dx").asDouble(),
                dy = sample.required("dy").asDouble(),
                rotationDeg = sample.required("rotationDeg").asDouble(),
                scale = sample.required("scale").asDouble(),
                confidence = sample.required("confidence").asDouble(),
            )
        }
        return TermuxGlobalMotionAnalysis(framesDecoded, frameRate, samples)
    }

    private fun parseObjectTracking(root: JObject): TermuxObjectTrackingAnalysis {
        val sourceOutUs = root.optional("sourceOutUs")?.let { value ->
            if (value is JNull) null else value.asLong()
        }
        val samples = root.required("samples").asArray().values.map { value ->
            val sample = value.asObject()
            TermuxObjectTrackingSample(
                frameIndex = sample.required("frameIndex").asInt(),
                timeUs = sample.required("timeUs").asLong(),
                dxNorm = sample.required("dxNorm").asDouble(),
                dyNorm = sample.required("dyNorm").asDouble(),
                centerXNorm = sample.required("centerXNorm").asDouble(),
                centerYNorm = sample.required("centerYNorm").asDouble(),
                confidence = sample.required("confidence").asDouble(),
            )
        }
        return TermuxObjectTrackingAnalysis(
            framesDecoded = root.required("framesDecoded").asInt(),
            frameWidth = root.required("frameWidth").asInt(),
            frameHeight = root.required("frameHeight").asInt(),
            frameRate = root.required("frameRate").asDouble(),
            sourceInUs = root.required("sourceInUs").asLong(),
            sourceOutUs = sourceOutUs,
            samples = samples,
        )
    }

    private fun parseAnalysis(root: JObject): TermuxMediaAnalysis {
        fun stringOrNull(key: String) = root.optional(key)?.let { if (it is JNull) null else runCatching { it.asString() }.getOrNull() }
        fun longOrNull(key: String) = root.optional(key)?.let { if (it is JNull) null else runCatching { it.asLong() }.getOrNull() }
        val streams = root.optional("streams")?.let { value ->
            if (value is JNull) emptyList() else value.asArray().values.mapNotNull { item ->
                val s = item as? JObject ?: return@mapNotNull null
                fun str(k: String) = s.optional(k)?.let { if (it is JNull) null else runCatching { it.asString() }.getOrNull() }
                fun int(k: String) = s.optional(k)?.let { if (it is JNull) null else runCatching { it.asInt() }.getOrNull() }
                fun dbl(k: String) = s.optional(k)?.let { if (it is JNull) null else runCatching { it.asDouble() }.getOrNull() }
                TermuxMediaStream(str("codecType"), str("codecName"), int("width"), int("height"), int("sampleRate"), int("channels"), dbl("frameRate"))
            }
        }.orEmpty()
        return TermuxMediaAnalysis(stringOrNull("formatName"), longOrNull("durationUs"), longOrNull("sizeBytes"), longOrNull("bitRate"), streams)
    }
}
