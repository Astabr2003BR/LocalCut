package com.localcut.editor.termux

import com.localcut.editor.data.project.JNull
import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asDouble
import com.localcut.editor.data.project.asInt
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required

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
        return buildString {
            append('{'); appendCommon(requestId, token, "CREATE_JOB")
            append(",\"jobType\":\"ANALYZE_MEDIA\",\"idempotencyKey\":"); append(jsonString(key))
            append(",\"input\":{\"mode\":\"MEDIA\",\"assetId\":"); append(jsonString(input.assetId))
            input.displayName?.let { append(",\"displayName\":"); append(jsonString(it)) }
            append(",\"mimeType\":"); append(jsonString(input.mimeType))
            append(",\"sizeBytes\":"); append(input.sizeBytes)
            input.sourceDurationUs?.let { append(",\"sourceDurationUs\":"); append(it) }
            append("}}\n")
        }
    }

    fun getJob(requestId: String, token: String, jobId: String) = jobRequest(requestId, token, "GET_JOB", jobId)
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
        val analysis = (output?.optional("analysis") as? JObject)?.let(::parseAnalysis)
        return TermuxEngineJob(
            id = job.required("id").asString(),
            type = job.required("type").asString(),
            status = TermuxEngineJobStatus.valueOf(job.required("status").asString()),
            progress = progress,
            errorCode = errorCode,
            analysis = analysis,
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
