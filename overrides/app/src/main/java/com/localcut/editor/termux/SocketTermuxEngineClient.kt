package com.localcut.editor.termux

import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asBoolean
import com.localcut.editor.data.project.asInt
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.required
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SocketTermuxEngineClient(
    private val host: String = TermuxEngineProtocol.DEFAULT_HOST,
    private val port: Int = TermuxEngineProtocol.DEFAULT_PORT,
    private val tokenProvider: () -> String?,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 3_000,
) : TermuxEngineClient {
    init {
        TermuxEngineEndpointPolicy.validate(host, port)
        require(connectTimeoutMs in 1..30_000) { "TERMUX_ENGINE_CONNECT_TIMEOUT_INVALID" }
        require(readTimeoutMs in 1..60_000) { "TERMUX_ENGINE_READ_TIMEOUT_INVALID" }
    }

    override suspend fun pair(pairingCode: String): Result<TermuxEnginePairing> =
        exchangeRequest { requestId ->
            TermuxEnginePairingRequestEncoder.encode(requestId, pairingCode)
        }.mapCatching(TermuxEngineResponseParser::parsePairing)

    override suspend fun ping(): Result<TermuxEnginePing> = exchange(TermuxEngineOperation.PING)
        .mapCatching(TermuxEngineResponseParser::parsePing)

    override suspend fun getStatus(): Result<TermuxEngineStatus> = exchange(TermuxEngineOperation.GET_STATUS)
        .mapCatching(TermuxEngineResponseParser::parseStatus)

    override suspend fun getCapabilities(): Result<TermuxEngineCapabilities> = exchange(TermuxEngineOperation.GET_CAPABILITIES)
        .mapCatching(TermuxEngineResponseParser::parseCapabilities)

    private suspend fun exchange(operation: TermuxEngineOperation): Result<com.localcut.editor.data.project.JObject> =
        exchangeRequest { requestId ->
            val token = TermuxEngineTokenPolicy.normalize(
                tokenProvider() ?: error("TERMUX_ENGINE_NOT_PAIRED"),
            )
            TermuxEngineRequestEncoder.encode(requestId, operation, token)
        }

    private suspend fun exchangeRequest(
        requestFactory: (requestId: String) -> String,
    ): Result<com.localcut.editor.data.project.JObject> = withContext(Dispatchers.IO) {
        runCatching {
            val requestId = UUID.randomUUID().toString()
            val request = requestFactory(requestId)
            Socket().use { socket ->
                socket.soTimeout = readTimeoutMs
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.getOutputStream().apply {
                    write(request.toByteArray(Charsets.UTF_8))
                    flush()
                }
                val rawResponse = readBoundedLine(socket)
                val root = runCatching { MiniJson.parse(rawResponse).asObject() }
                    .getOrElse { throw IllegalStateException("TERMUX_ENGINE_RESPONSE_INVALID") }
                val responseRequestId = root.required("requestId").asString()
                require(responseRequestId == requestId) { "TERMUX_ENGINE_REQUEST_ID_MISMATCH" }
                if (!root.required("ok").asBoolean()) {
                    throw IllegalStateException(
                        runCatching { root.required("code").asString() }
                            .getOrDefault("TERMUX_ENGINE_FAILED"),
                    )
                }
                root
            }
        }
    }

    private fun readBoundedLine(socket: Socket): String {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        while (output.size() <= TermuxEngineProtocol.MAX_RESPONSE_BYTES) {
            val next = input.read()
            if (next == -1) error("TERMUX_ENGINE_RESPONSE_TRUNCATED")
            if (next == '\n'.code) return output.toByteArray().toString(Charsets.UTF_8)
            output.write(next)
        }
        error("TERMUX_ENGINE_RESPONSE_TOO_LARGE")
    }
}

internal object TermuxEnginePairingRequestEncoder {
    fun encode(requestId: String, pairingCode: String): String {
        require(requestId.matches(Regex("^[A-Za-z0-9_-]{8,128}$"))) { "TERMUX_ENGINE_REQUEST_ID_INVALID" }
        val normalizedCode = TermuxEnginePairingCodePolicy.normalize(pairingCode)
        return buildString {
            append('{')
            append("\"protocolVersion\":"); append(TermuxEngineProtocol.PROTOCOL_VERSION); append(',')
            append("\"projectId\":"); append(jsonString(TermuxEngineProtocol.PROJECT_ID)); append(',')
            append("\"requestId\":"); append(jsonString(requestId)); append(',')
            append("\"operation\":\"PAIR\",")
            append("\"pairingCode\":"); append(jsonString(normalizedCode))
            append("}\n")
        }
    }
}

internal object TermuxEngineRequestEncoder {
    fun encode(requestId: String, operation: TermuxEngineOperation, token: String): String {
        require(requestId.matches(Regex("^[A-Za-z0-9_-]{8,128}$"))) { "TERMUX_ENGINE_REQUEST_ID_INVALID" }
        val normalizedToken = TermuxEngineTokenPolicy.normalize(token)
        return buildString {
            append('{')
            append("\"protocolVersion\":"); append(TermuxEngineProtocol.PROTOCOL_VERSION); append(',')
            append("\"projectId\":"); append(jsonString(TermuxEngineProtocol.PROJECT_ID)); append(',')
            append("\"requestId\":"); append(jsonString(requestId)); append(',')
            append("\"token\":"); append(jsonString(normalizedToken)); append(',')
            append("\"operation\":"); append(jsonString(operation.name))
            append("}\n")
        }
    }
}

internal object TermuxEngineResponseParser {
    fun parsePairing(root: com.localcut.editor.data.project.JObject): TermuxEnginePairing {
        val protocol = requireProtocol(root)
        val token = TermuxEngineTokenPolicy.normalize(root.required("token").asString())
        return TermuxEnginePairing(protocol, token)
    }

    fun parsePing(root: com.localcut.editor.data.project.JObject): TermuxEnginePing {
        val protocol = requireProtocol(root)
        val status = root.required("status").asString()
        require(status == "READY") { "TERMUX_ENGINE_NOT_READY" }
        return TermuxEnginePing(protocol, status)
    }

    fun parseStatus(root: com.localcut.editor.data.project.JObject): TermuxEngineStatus {
        val protocol = requireProtocol(root)
        val projectId = root.required("projectId").asString()
        require(projectId == TermuxEngineProtocol.PROJECT_ID) { "TERMUX_ENGINE_PROJECT_MISMATCH" }
        return TermuxEngineStatus(
            protocolVersion = protocol,
            engineVersion = root.required("engineVersion").asString(),
            projectId = projectId,
            status = root.required("status").asString().also {
                require(it.isNotBlank()) { "TERMUX_ENGINE_STATUS_INVALID" }
            },
            pid = root.required("pid").asLong(),
        )
    }

    fun parseCapabilities(root: com.localcut.editor.data.project.JObject): TermuxEngineCapabilities {
        val protocol = requireProtocol(root)
        val capabilities = root.required("capabilities").asArray().values.map { it.asString() }.toSet()
        require(capabilities.containsAll(TermuxEngineProtocol.REQUIRED_CAPABILITIES)) {
            "TERMUX_ENGINE_CAPABILITIES_INCOMPLETE"
        }
        return TermuxEngineCapabilities(protocol, capabilities)
    }

    private fun requireProtocol(root: com.localcut.editor.data.project.JObject): Int {
        val protocol = root.required("protocolVersion").asInt()
        require(protocol == TermuxEngineProtocol.PROTOCOL_VERSION) { "TERMUX_ENGINE_PROTOCOL_MISMATCH" }
        return protocol
    }
}
