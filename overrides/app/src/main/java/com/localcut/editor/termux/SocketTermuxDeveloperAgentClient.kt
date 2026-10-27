package com.localcut.editor.termux

import com.localcut.editor.bridge.agent.BridgeTermuxAgentClient
import com.localcut.editor.bridge.agent.BridgeTermuxAgentRequest
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asBoolean
import com.localcut.editor.data.project.asInt
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.required
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class SocketTermuxDeveloperAgentClient(
    private val host: String = TermuxEngineProtocol.DEFAULT_HOST,
    private val port: Int = TermuxAgentApprovalProtocol.PORT,
    private val tokenProvider: () -> String?,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 900_000,
) : BridgeTermuxAgentClient {
    init {
        TermuxEngineEndpointPolicy.validate(host, port)
        require(connectTimeoutMs in 1..30_000) { "TERMUX_AGENT_CONNECT_TIMEOUT_INVALID" }
        require(readTimeoutMs in 1..900_000) { "TERMUX_AGENT_READ_TIMEOUT_INVALID" }
    }

    override fun execute(taskId: String, request: BridgeTermuxAgentRequest): Result<String> = runCatching {
        require(taskId.matches(Regex("^[A-Za-z0-9._:-]{8,128}$"))) { "TERMUX_AGENT_TASK_ID_INVALID" }
        val token = TermuxEngineTokenPolicy.normalize(
            tokenProvider() ?: error("TERMUX_ENGINE_NOT_PAIRED"),
        )
        val requestId = UUID.randomUUID().toString()
        val requestJson = encode(requestId, taskId, token, request)
        Socket().use { socket ->
            socket.soTimeout = readTimeoutMs
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.getOutputStream().apply {
                write(requestJson.toByteArray(Charsets.UTF_8))
                flush()
            }
            val raw = readResponse(socket)
            require(!raw.contains(token)) { "TERMUX_AGENT_RESPONSE_SECRET_ECHO" }
            val root = MiniJson.parse(raw).asObject()
            require(root.required("protocolVersion").asInt() == TermuxEngineProtocol.PROTOCOL_VERSION) {
                "TERMUX_AGENT_PROTOCOL_MISMATCH"
            }
            require(root.required("requestId").asString() == requestId) {
                "TERMUX_AGENT_REQUEST_ID_MISMATCH"
            }
            if (!root.required("ok").asBoolean()) {
                throw IllegalStateException(
                    runCatching { root.required("code").asString() }
                        .getOrDefault("TERMUX_AGENT_FAILED"),
                )
            }
            raw
        }
    }

    private fun encode(
        requestId: String,
        taskId: String,
        token: String,
        request: BridgeTermuxAgentRequest,
    ): String = buildString {
        append('{')
        append("\"protocolVersion\":"); append(TermuxEngineProtocol.PROTOCOL_VERSION)
        append(",\"projectId\":"); append(jsonString(TermuxEngineProtocol.PROJECT_ID))
        append(",\"requestId\":"); append(jsonString(requestId))
        append(",\"token\":"); append(jsonString(token))
        when (request) {
            BridgeTermuxAgentRequest.GetWorkspaceStatus -> {
                append(",\"operation\":\"GET_WORKSPACE_STATUS\"")
            }
            is BridgeTermuxAgentRequest.ListWorkspaceFiles -> {
                append(",\"operation\":\"LIST_WORKSPACE_FILES\"")
                append(",\"path\":"); append(jsonString(request.path))
                append(",\"limit\":"); append(request.limit)
            }
            is BridgeTermuxAgentRequest.ReadWorkspaceFile -> {
                append(",\"operation\":\"READ_WORKSPACE_FILE\"")
                append(",\"path\":"); append(jsonString(request.path))
            }
            is BridgeTermuxAgentRequest.ApplyTextPatch -> {
                append(",\"operation\":\"APPLY_TEXT_PATCH\"")
                append(",\"path\":"); append(jsonString(request.path))
                append(",\"expectedSha256\":"); append(jsonString(request.expectedSha256))
                append(",\"oldText\":"); append(jsonString(request.oldText))
                append(",\"newText\":"); append(jsonString(request.newText))
            }
            is BridgeTermuxAgentRequest.RunAgentOperation -> {
                append(",\"operation\":\"RUN_AGENT_OPERATION\"")
                append(",\"agentOperation\":"); append(jsonString(request.operation))
                append(",\"taskId\":"); append(jsonString(taskId))
                append(",\"input\":{}")
            }
        }
        append("}\n")
    }

    private fun readResponse(socket: Socket): String {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        while (output.size() <= MAX_RESPONSE_BYTES) {
            val next = input.read()
            if (next == -1) error("TERMUX_AGENT_RESPONSE_TRUNCATED")
            if (next == '\n'.code) {
                return output.toByteArray().toString(Charsets.UTF_8)
            }
            output.write(next)
        }
        error("TERMUX_AGENT_RESPONSE_TOO_LARGE")
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_048_576
    }
}
