package com.localcut.editor.termux

import com.localcut.editor.bridge.agent.BridgeTermuxAgentRequest
import com.localcut.editor.bridge.agent.BridgeTermuxDeveloperClient
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SocketTermuxDeveloperAgentClient(
    private val host: String = TermuxEngineProtocol.DEFAULT_HOST,
    private val port: Int = TermuxDeveloperAgentProtocol.PORT,
    private val tokenProvider: () -> String?,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 920_000,
) : BridgeTermuxDeveloperClient {
    init {
        TermuxEngineEndpointPolicy.validate(host, port)
        require(connectTimeoutMs in 1..30_000) { "TERMUX_AGENT_CONNECT_TIMEOUT_INVALID" }
        require(readTimeoutMs in 1..1_000_000) { "TERMUX_AGENT_READ_TIMEOUT_INVALID" }
    }

    override suspend fun execute(request: BridgeTermuxAgentRequest, taskId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(TASK_ID.matches(taskId)) { "TERMUX_AGENT_TASK_ID_INVALID" }
                val requestId = UUID.randomUUID().toString()
                val payload = TermuxDeveloperAgentRequestEncoder.encode(
                    requestId = requestId,
                    token = token(),
                    taskId = taskId,
                    request = request,
                )
                Socket().use { socket ->
                    socket.soTimeout = readTimeoutMs
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                    socket.getOutputStream().apply {
                        write(payload.toByteArray(Charsets.UTF_8))
                        flush()
                    }
                    val response = readResponse(socket)
                    val root = runCatching { MiniJson.parse(response).asObject() }
                        .getOrElse { error("TERMUX_AGENT_RESPONSE_INVALID") }
                    require(root.required("protocolVersion").asInt() == TermuxEngineProtocol.PROTOCOL_VERSION) {
                        "TERMUX_AGENT_PROTOCOL_MISMATCH"
                    }
                    require(root.required("requestId").asString() == requestId) {
                        "TERMUX_AGENT_REQUEST_ID_MISMATCH"
                    }
                    if (!root.required("ok").asBoolean()) {
                        val code = runCatching { root.required("code").asString() }
                            .getOrDefault("TERMUX_AGENT_FAILED")
                        throw IllegalStateException(
                            if (ERROR_CODE.matches(code)) code else "TERMUX_AGENT_FAILED",
                        )
                    }
                    response
                }
            }
        }

    private fun token(): String = TermuxEngineTokenPolicy.normalize(
        tokenProvider() ?: error("TERMUX_ENGINE_NOT_PAIRED"),
    )

    private fun readResponse(socket: Socket): String {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        while (output.size() <= TermuxDeveloperAgentProtocol.MAX_RESPONSE_BYTES) {
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
        val TASK_ID = Regex("^[A-Za-z0-9._:-]{8,128}$")
        val ERROR_CODE = Regex("^[A-Z0-9_]{3,96}$")
    }
}

object TermuxDeveloperAgentProtocol {
    const val PORT = 43128
    const val MAX_RESPONSE_BYTES = 2_621_440
}

internal object TermuxDeveloperAgentRequestEncoder {
    private val REQUEST_ID = Regex("^[A-Za-z0-9._:-]{8,128}$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun encode(
        requestId: String,
        token: String,
        taskId: String,
        request: BridgeTermuxAgentRequest,
    ): String {
        require(REQUEST_ID.matches(requestId)) { "TERMUX_AGENT_REQUEST_ID_INVALID" }
        val normalizedToken = TermuxEngineTokenPolicy.normalize(token)
        return buildString {
            append('{')
            append("\"protocolVersion\":"); append(TermuxEngineProtocol.PROTOCOL_VERSION)
            append(",\"projectId\":"); append(jsonString(TermuxEngineProtocol.PROJECT_ID))
            append(",\"requestId\":"); append(jsonString(requestId))
            append(",\"token\":"); append(jsonString(normalizedToken))
            append(",\"operation\":"); append(jsonString(request.operation))
            when (request) {
                BridgeTermuxAgentRequest.GetWorkspaceStatus -> Unit
                is BridgeTermuxAgentRequest.ListWorkspaceFiles -> {
                    require(request.limit in 1..500) { "WORKSPACE_LIMIT_INVALID" }
                    append(",\"path\":"); append(jsonString(request.path))
                    append(",\"limit\":"); append(request.limit)
                }
                is BridgeTermuxAgentRequest.ReadWorkspaceFile -> {
                    append(",\"path\":"); append(jsonString(request.path))
                }
                is BridgeTermuxAgentRequest.ApplyTextPatch -> {
                    require(SHA256.matches(request.expectedSha256)) { "WORKSPACE_SHA256_INVALID" }
                    append(",\"path\":"); append(jsonString(request.path))
                    append(",\"expectedSha256\":"); append(jsonString(request.expectedSha256))
                    append(",\"oldText\":"); append(jsonString(request.oldText))
                    append(",\"newText\":"); append(jsonString(request.newText))
                }
                is BridgeTermuxAgentRequest.RunAgentOperation -> {
                    require(request.agentOperation.matches(Regex("^[A-Z0-9_]{3,96}$"))) {
                        "AGENT_OPERATION_INVALID"
                    }
                    append(",\"agentOperation\":"); append(jsonString(request.agentOperation))
                    append(",\"taskId\":"); append(jsonString(taskId))
                    append(",\"input\":{}")
                }
                is BridgeTermuxAgentRequest.CommitWorkspace -> {
                    require(request.expectedBranch.matches(Regex("^[A-Za-z0-9._/-]{1,200}$"))) {
                        "AGENT_BRANCH_INVALID"
                    }
                    require(request.message.isNotBlank() && request.message.length <= 160 && request.message.none { it.code < 32 }) {
                        "AGENT_COMMIT_MESSAGE_INVALID"
                    }
                    require(request.paths.isNotEmpty() && request.paths.size <= 50) { "AGENT_PATHS_INVALID" }
                    append(",\"agentOperation\":\"GIT_COMMIT\"")
                    append(",\"taskId\":"); append(jsonString(taskId))
                    append(",\"input\":{\"expectedBranch\":"); append(jsonString(request.expectedBranch))
                    append(",\"message\":"); append(jsonString(request.message))
                    append(",\"paths\":[")
                    request.paths.forEachIndexed { index, path ->
                        if (index > 0) append(',')
                        append(jsonString(path))
                    }
                    append("]}")
                }
                is BridgeTermuxAgentRequest.PushWorkspace -> {
                    require(request.expectedBranch.matches(Regex("^[A-Za-z0-9._/-]{1,200}$"))) {
                        "AGENT_BRANCH_INVALID"
                    }
                    append(",\"agentOperation\":\"GIT_PUSH\"")
                    append(",\"taskId\":"); append(jsonString(taskId))
                    append(",\"input\":{\"expectedBranch\":"); append(jsonString(request.expectedBranch)); append('}')
                }
            }
            append("}\n")
        }
    }
}
