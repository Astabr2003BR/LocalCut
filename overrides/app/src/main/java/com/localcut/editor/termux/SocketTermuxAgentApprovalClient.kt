package com.localcut.editor.termux

import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asBoolean
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

class SocketTermuxAgentApprovalClient(
    private val host: String = TermuxEngineProtocol.DEFAULT_HOST,
    private val port: Int = TermuxAgentApprovalProtocol.PORT,
    private val tokenProvider: () -> String?,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 5_000,
) : TermuxAgentApprovalClient {
    init {
        TermuxEngineEndpointPolicy.validate(host, port)
        require(connectTimeoutMs in 1..30_000) { "TERMUX_AGENT_CONNECT_TIMEOUT_INVALID" }
        require(readTimeoutMs in 1..60_000) { "TERMUX_AGENT_READ_TIMEOUT_INVALID" }
    }

    override suspend fun getPendingApprovals(): Result<List<TermuxAgentApproval>> =
        exchange { requestId -> TermuxAgentApprovalRequestEncoder.pending(requestId, token()) }
            .mapCatching(TermuxAgentApprovalResponseParser::parsePending)

    override suspend fun resolveApproval(
        approvalId: String,
        decision: TermuxApprovalDecision,
    ): Result<TermuxAgentApproval> = exchange { requestId ->
        TermuxAgentApprovalRequestEncoder.resolve(requestId, token(), approvalId, decision)
    }.mapCatching(TermuxAgentApprovalResponseParser::parseApprovalResponse)

    private suspend fun exchange(factory: (String) -> String): Result<JObject> = withContext(Dispatchers.IO) {
        runCatching {
            val requestId = UUID.randomUUID().toString()
            Socket().use { socket ->
                socket.soTimeout = readTimeoutMs
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.getOutputStream().apply {
                    write(factory(requestId).toByteArray(Charsets.UTF_8))
                    flush()
                }
                val root = readResponse(socket)
                require(root.required("requestId").asString() == requestId) {
                    "TERMUX_AGENT_REQUEST_ID_MISMATCH"
                }
                if (!root.required("ok").asBoolean()) {
                    throw IllegalStateException(
                        runCatching { root.required("code").asString() }
                            .getOrDefault("TERMUX_AGENT_FAILED"),
                    )
                }
                root
            }
        }
    }

    private fun token() = TermuxEngineTokenPolicy.normalize(
        tokenProvider() ?: error("TERMUX_ENGINE_NOT_PAIRED"),
    )

    private fun readResponse(socket: Socket): JObject {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        while (output.size() <= TermuxAgentApprovalProtocol.MAX_RESPONSE_BYTES) {
            val next = input.read()
            if (next == -1) error("TERMUX_AGENT_RESPONSE_TRUNCATED")
            if (next == '\n'.code) {
                return runCatching {
                    MiniJson.parse(output.toByteArray().toString(Charsets.UTF_8)).asObject()
                }.getOrElse { error("TERMUX_AGENT_RESPONSE_INVALID") }
            }
            output.write(next)
        }
        error("TERMUX_AGENT_RESPONSE_TOO_LARGE")
    }
}

internal object TermuxAgentApprovalRequestEncoder {
    private val REQUEST_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
    private val APPROVAL_ID = Regex("^[0-9a-fA-F-]{36}$")

    fun pending(requestId: String, token: String): String = buildString {
        append('{'); appendCommon(requestId, token, "GET_PENDING_APPROVALS"); append("}\n")
    }

    fun resolve(
        requestId: String,
        token: String,
        approvalId: String,
        decision: TermuxApprovalDecision,
    ): String {
        require(APPROVAL_ID.matches(approvalId)) { "TERMUX_AGENT_APPROVAL_ID_INVALID" }
        return buildString {
            append('{'); appendCommon(requestId, token, "RESOLVE_APPROVAL")
            append(",\"approvalId\":"); append(jsonString(approvalId.lowercase()))
            append(",\"decision\":"); append(jsonString(decision.name)); append("}\n")
        }
    }

    private fun StringBuilder.appendCommon(requestId: String, token: String, operation: String) {
        require(REQUEST_ID.matches(requestId)) { "TERMUX_AGENT_REQUEST_ID_INVALID" }
        append("\"protocolVersion\":"); append(TermuxEngineProtocol.PROTOCOL_VERSION)
        append(",\"projectId\":"); append(jsonString(TermuxEngineProtocol.PROJECT_ID))
        append(",\"requestId\":"); append(jsonString(requestId))
        append(",\"token\":"); append(jsonString(TermuxEngineTokenPolicy.normalize(token)))
        append(",\"operation\":"); append(jsonString(operation))
    }
}

internal object TermuxAgentApprovalResponseParser {
    fun parsePending(root: JObject): List<TermuxAgentApproval> =
        root.required("approvals").asArray().values.map { parseApproval(it.asObject()) }

    fun parseApprovalResponse(root: JObject): TermuxAgentApproval =
        parseApproval(root.required("approval").asObject())

    private fun parseApproval(root: JObject): TermuxAgentApproval {
        val id = root.required("id").asString()
        require(id.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "TERMUX_AGENT_APPROVAL_ID_INVALID" }
        val operation = root.required("operation").asString()
        require(operation.matches(Regex("^[A-Z0-9_]{3,96}$"))) { "TERMUX_AGENT_OPERATION_INVALID" }
        val status = root.required("status").asString()
        require(status in setOf("WAITING_APPROVAL", "APPROVED", "DENIED")) {
            "TERMUX_AGENT_APPROVAL_STATUS_INVALID"
        }
        return TermuxAgentApproval(
            id = id.lowercase(),
            operation = operation,
            status = status,
            requestedAt = root.required("requestedAt").asLong().also { require(it >= 0L) },
        )
    }
}
