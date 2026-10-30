package com.localcut.editor.termux

import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asBoolean
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.required
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SocketTermuxEngineJobClient(
    private val host: String = TermuxEngineProtocol.DEFAULT_HOST,
    private val port: Int = TermuxEngineProtocol.DEFAULT_PORT,
    private val tokenProvider: () -> String?,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 30_000,
) : TermuxEngineJobClient {
    private val core = SocketTermuxEngineClient(host, port, tokenProvider, connectTimeoutMs, minOf(readTimeoutMs, 60_000))

    init {
        TermuxEngineEndpointPolicy.validate(host, port)
        require(connectTimeoutMs in 1..30_000)
        require(readTimeoutMs in 1..300_000)
    }

    override suspend fun pair(pairingCode: String) = core.pair(pairingCode)
    override suspend fun ping() = core.ping()
    override suspend fun getStatus() = core.getStatus()
    override suspend fun getCapabilities() = core.getCapabilities()

    override suspend fun createAnalyzeMediaJob(input: TermuxAnalyzeMediaInput, idempotencyKey: String) =
        exchange { requestId -> TermuxEngineJobRequestEncoder.createAnalyzeMedia(requestId, token(), idempotencyKey, input) }
            .mapCatching(TermuxEngineJobResponseParser::parseJob)

    override suspend fun getJob(jobId: String) =
        exchange { requestId -> TermuxEngineJobRequestEncoder.getJob(requestId, token(), jobId) }
            .mapCatching(TermuxEngineJobResponseParser::parseJob)

    override suspend fun cancelJob(jobId: String) =
        exchange { requestId -> TermuxEngineJobRequestEncoder.cancelJob(requestId, token(), jobId) }
            .mapCatching(TermuxEngineJobResponseParser::parseJob)

    override suspend fun stageMedia(jobId: String, source: TermuxEngineMediaSource): Result<TermuxEngineStageResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestId = UUID.randomUUID().toString()
                val header = TermuxEngineJobRequestEncoder.stageMedia(requestId, token(), jobId, source.contentLength)
                Socket().use { socket ->
                    socket.soTimeout = readTimeoutMs
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var written = 0L
                    val output = socket.getOutputStream()
                    output.write(header.toByteArray(Charsets.UTF_8))
                    source.openStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            if (count == 0) continue
                            written += count
                            require(written <= source.contentLength) { "TERMUX_ENGINE_MEDIA_LENGTH_MISMATCH" }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                    }
                    require(written == source.contentLength) { "TERMUX_ENGINE_MEDIA_LENGTH_MISMATCH" }
                    output.flush(); socket.shutdownOutput()
                    val root = readResponse(socket, requestId)
                    val localSha = digest.digest().joinToString("") { "%02x".format(it) }
                    TermuxEngineJobResponseParser.parseStage(root).also {
                        require(it.sizeBytes == source.contentLength) { "TERMUX_ENGINE_TRANSFER_SIZE_MISMATCH" }
                        require(it.sha256.equals(localSha, ignoreCase = true)) { "TERMUX_ENGINE_TRANSFER_DIGEST_MISMATCH" }
                    }
                }
            }
        }

    override suspend fun downloadGeneratedMedia(
        jobId: String,
        destination: File,
    ): Result<TermuxGeneratedMediaDownload> = withContext(Dispatchers.IO) {
        runCatching {
            require(!destination.exists()) { "TERMUX_GENERATED_MEDIA_DESTINATION_EXISTS" }
            val absoluteDestination = destination.absoluteFile
            val parent = requireNotNull(absoluteDestination.parentFile) { "TERMUX_GENERATED_MEDIA_DESTINATION_INVALID" }
            if (!parent.exists()) require(parent.mkdirs()) { "TERMUX_GENERATED_MEDIA_DESTINATION_CREATE_FAILED" }
            require(parent.isDirectory) { "TERMUX_GENERATED_MEDIA_DESTINATION_INVALID" }

            val requestId = UUID.randomUUID().toString()
            val request = TermuxEngineJobRequestEncoder.getGeneratedMedia(requestId, token(), jobId)
            val temp = File(parent, ".${absoluteDestination.name}.${UUID.randomUUID()}.part")
            require(temp.createNewFile()) { "TERMUX_GENERATED_MEDIA_TEMP_CREATE_FAILED" }
            try {
                Socket().use { socket ->
                    socket.soTimeout = readTimeoutMs
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                    socket.getOutputStream().apply {
                        write(request.toByteArray(Charsets.UTF_8))
                        flush()
                    }

                    val root = readResponse(socket, requestId)
                    val header = TermuxEngineJobResponseParser.parseGeneratedMediaHeader(root)
                    require(header.job.status == TermuxEngineJobStatus.SUCCEEDED) {
                        "TERMUX_GENERATED_MEDIA_JOB_NOT_SUCCEEDED"
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    var remaining = header.contentLength
                    FileOutputStream(temp, false).use { output ->
                        val input = socket.getInputStream()
                        val buffer = ByteArray(64 * 1024)
                        while (remaining > 0L) {
                            val requested = minOf(buffer.size.toLong(), remaining).toInt()
                            val count = input.read(buffer, 0, requested)
                            if (count == -1) error("TERMUX_GENERATED_MEDIA_TRUNCATED")
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            remaining -= count.toLong()
                        }
                        output.flush()
                        output.fd.sync()
                    }

                    require(temp.length() == header.contentLength) { "TERMUX_GENERATED_MEDIA_LENGTH_MISMATCH" }
                    val localSha = digest.digest().joinToString("") { "%02x".format(it) }
                    require(localSha.equals(header.sha256, ignoreCase = true)) {
                        "TERMUX_GENERATED_MEDIA_DIGEST_MISMATCH"
                    }
                    require(temp.renameTo(absoluteDestination)) { "TERMUX_GENERATED_MEDIA_PUBLISH_FAILED" }

                    TermuxGeneratedMediaDownload(
                        job = header.job,
                        media = header.media,
                        file = absoluteDestination,
                    )
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    private suspend fun exchange(factory: (String) -> String): Result<JObject> = withContext(Dispatchers.IO) {
        runCatching {
            val requestId = UUID.randomUUID().toString()
            Socket().use { socket ->
                socket.soTimeout = readTimeoutMs
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.getOutputStream().apply { write(factory(requestId).toByteArray(Charsets.UTF_8)); flush() }
                readResponse(socket, requestId)
            }
        }
    }

    private fun token() = TermuxEngineTokenPolicy.normalize(tokenProvider() ?: error("TERMUX_ENGINE_NOT_PAIRED"))

    private fun readResponse(socket: Socket, requestId: String): JObject {
        val raw = readBoundedLine(socket)
        val root = runCatching { MiniJson.parse(raw).asObject() }.getOrElse { error("TERMUX_ENGINE_RESPONSE_INVALID") }
        require(root.required("requestId").asString() == requestId) { "TERMUX_ENGINE_REQUEST_ID_MISMATCH" }
        if (!root.required("ok").asBoolean()) {
            throw IllegalStateException(runCatching { root.required("code").asString() }.getOrDefault("TERMUX_ENGINE_FAILED"))
        }
        return root
    }

    private fun readBoundedLine(socket: Socket): String {
        val input = socket.getInputStream(); val output = ByteArrayOutputStream()
        while (output.size() <= TermuxEngineJobCapabilities.MAX_RESPONSE_BYTES) {
            val next = input.read()
            if (next == -1) error("TERMUX_ENGINE_RESPONSE_TRUNCATED")
            if (next == '\n'.code) return output.toByteArray().toString(Charsets.UTF_8)
            output.write(next)
        }
        error("TERMUX_ENGINE_RESPONSE_TOO_LARGE")
    }
}
