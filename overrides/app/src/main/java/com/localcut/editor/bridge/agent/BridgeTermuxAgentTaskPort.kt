package com.localcut.editor.bridge.agent

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val SHA256 = Regex("^[0-9a-f]{64}$")
private val AGENT_OPERATION = Regex("^[A-Z0-9_]{3,96}$")

private val ALLOWED_AGENT_OPERATIONS = setOf(
    "GIT_STATUS",
    "GIT_DIFF",
    "RUN_TERMUX_TESTS",
    "RUN_ANDROID_UNIT_TESTS",
    "BUILD_DEBUG_APK",
    "MERGE_MAIN",
    "FORCE_PUSH",
    "DELETE_PROJECT",
    "DELETE_REPOSITORY",
    "DELETE_DATABASE",
    "DELETE_PRODUCTION_DATA",
    "DROP_DATABASE",
    "DROP_TABLE",
    "CHANGE_APK_SIGNING",
    "CHANGE_KEYSTORE",
    "ALTER_SECRETS",
    "REVOKE_CREDENTIALS",
    "PUBLISH_PLAY_STORE",
    "PUBLISH_PRODUCTION_RELEASE",
    "OVERWRITE_CRITICAL_BACKUP",
    "DESTRUCTIVE_MIGRATION",
)

enum class BridgeTermuxAgentTaskState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
}

sealed interface BridgeTermuxAgentRequest {
    data object GetWorkspaceStatus : BridgeTermuxAgentRequest

    data class ListWorkspaceFiles(
        val path: String = "",
        val limit: Int = 200,
    ) : BridgeTermuxAgentRequest {
        init {
            require(path.length <= 512) { "TERMUX_WORKSPACE_PATH_INVALID" }
            require(limit in 1..500) { "TERMUX_WORKSPACE_LIMIT_INVALID" }
        }
    }

    data class ReadWorkspaceFile(val path: String) : BridgeTermuxAgentRequest {
        init {
            require(path.isNotBlank() && path.length <= 512) { "TERMUX_WORKSPACE_PATH_INVALID" }
        }
    }

    data class ApplyTextPatch(
        val path: String,
        val expectedSha256: String,
        val oldText: String,
        val newText: String,
    ) : BridgeTermuxAgentRequest {
        init {
            require(path.isNotBlank() && path.length <= 512) { "TERMUX_WORKSPACE_PATH_INVALID" }
            require(SHA256.matches(expectedSha256)) { "TERMUX_WORKSPACE_SHA256_INVALID" }
            require(oldText.isNotEmpty() && oldText.length <= 12_000) { "TERMUX_WORKSPACE_OLD_TEXT_INVALID" }
            require(newText.length <= 12_000) { "TERMUX_WORKSPACE_NEW_TEXT_INVALID" }
        }
    }

    data class RunAgentOperation(val operation: String) : BridgeTermuxAgentRequest {
        init {
            require(AGENT_OPERATION.matches(operation) && operation in ALLOWED_AGENT_OPERATIONS) {
                "TERMUX_AGENT_OPERATION_NOT_ALLOWED"
            }
        }
    }
}

data class BridgeTermuxAgentTaskSnapshot(
    val taskId: String,
    val state: BridgeTermuxAgentTaskState,
    val operation: String,
    val resultJson: String? = null,
    val errorCode: String? = null,
)

fun interface BridgeBackgroundTaskLauncher {
    fun launch(block: () -> Unit)
}

interface BridgeTermuxAgentClient {
    fun execute(taskId: String, request: BridgeTermuxAgentRequest): Result<String>
}

interface BridgeTermuxAgentPort {
    fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot
    fun get(taskId: String): BridgeTermuxAgentTaskSnapshot?
}

class AsyncBridgeTermuxAgentPort(
    private val client: BridgeTermuxAgentClient,
    private val launcher: BridgeBackgroundTaskLauncher,
) : BridgeTermuxAgentPort {
    private val tasks = ConcurrentHashMap<String, BridgeTermuxAgentTaskSnapshot>()

    override fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot {
        val taskId = UUID.randomUUID().toString()
        val queued = BridgeTermuxAgentTaskSnapshot(
            taskId = taskId,
            state = BridgeTermuxAgentTaskState.QUEUED,
            operation = request.operationName(),
        )
        tasks[taskId] = queued
        launcher.launch {
            tasks.computeIfPresent(taskId) { _, current -> current.copy(state = BridgeTermuxAgentTaskState.RUNNING) }
            val result = client.execute(taskId, request)
            tasks.computeIfPresent(taskId) { _, current ->
                result.fold(
                    onSuccess = { json ->
                        current.copy(
                            state = BridgeTermuxAgentTaskState.SUCCEEDED,
                            resultJson = json,
                            errorCode = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            state = BridgeTermuxAgentTaskState.FAILED,
                            resultJson = null,
                            errorCode = sanitizeErrorCode(error.message),
                        )
                    },
                )
            }
        }
        return tasks[taskId] ?: queued
    }

    override fun get(taskId: String): BridgeTermuxAgentTaskSnapshot? = tasks[taskId]

    private fun sanitizeErrorCode(message: String?): String = message
        ?.takeIf { it.matches(Regex("^[A-Z0-9_]{3,96}$")) }
        ?: "TERMUX_AGENT_FAILED"
}

object UnavailableBridgeTermuxAgentPort : BridgeTermuxAgentPort {
    override fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot =
        BridgeTermuxAgentTaskSnapshot(
            taskId = UUID.randomUUID().toString(),
            state = BridgeTermuxAgentTaskState.FAILED,
            operation = request.operationName(),
            errorCode = "TERMUX_AGENT_UNAVAILABLE",
        )

    override fun get(taskId: String): BridgeTermuxAgentTaskSnapshot? = null
}

private fun BridgeTermuxAgentRequest.operationName(): String = when (this) {
    BridgeTermuxAgentRequest.GetWorkspaceStatus -> "GET_WORKSPACE_STATUS"
    is BridgeTermuxAgentRequest.ListWorkspaceFiles -> "LIST_WORKSPACE_FILES"
    is BridgeTermuxAgentRequest.ReadWorkspaceFile -> "READ_WORKSPACE_FILE"
    is BridgeTermuxAgentRequest.ApplyTextPatch -> "APPLY_TEXT_PATCH"
    is BridgeTermuxAgentRequest.RunAgentOperation -> "RUN_AGENT_OPERATION:$operation"
}
