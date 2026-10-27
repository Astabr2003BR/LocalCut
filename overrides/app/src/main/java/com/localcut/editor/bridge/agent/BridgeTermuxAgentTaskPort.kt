package com.localcut.editor.bridge.agent

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface BridgeTermuxAgentRequest {
    val operation: String

    data object GetWorkspaceStatus : BridgeTermuxAgentRequest {
        override val operation: String = "GET_WORKSPACE_STATUS"
    }

    data class ListWorkspaceFiles(val path: String, val limit: Int) : BridgeTermuxAgentRequest {
        override val operation: String = "LIST_WORKSPACE_FILES"
    }

    data class ReadWorkspaceFile(val path: String) : BridgeTermuxAgentRequest {
        override val operation: String = "READ_WORKSPACE_FILE"
    }

    data class ApplyTextPatch(
        val path: String,
        val expectedSha256: String,
        val oldText: String,
        val newText: String,
    ) : BridgeTermuxAgentRequest {
        override val operation: String = "APPLY_TEXT_PATCH"
    }

    data class RunAgentOperation(val agentOperation: String) : BridgeTermuxAgentRequest {
        override val operation: String = "RUN_AGENT_OPERATION"
    }

    data class CommitWorkspace(
        val expectedBranch: String,
        val message: String,
        val paths: List<String>,
    ) : BridgeTermuxAgentRequest {
        override val operation: String = "RUN_AGENT_OPERATION"
    }

    data class PushWorkspace(val expectedBranch: String) : BridgeTermuxAgentRequest {
        override val operation: String = "RUN_AGENT_OPERATION"
    }
}

interface BridgeTermuxDeveloperClient {
    suspend fun execute(request: BridgeTermuxAgentRequest, taskId: String): Result<String>
}

enum class BridgeTermuxAgentTaskState { QUEUED, RUNNING, SUCCEEDED, FAILED }

data class BridgeTermuxAgentTaskSnapshot(
    val taskId: String,
    val operation: String,
    val state: BridgeTermuxAgentTaskState,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val resultJson: String? = null,
    val errorCode: String? = null,
)

interface BridgeTermuxAgentTaskPort {
    fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot
    fun get(taskId: String): BridgeTermuxAgentTaskSnapshot?
}

object UnavailableBridgeTermuxAgentTaskPort : BridgeTermuxAgentTaskPort {
    override fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot =
        throw IllegalStateException("TERMUX_AGENT_UNAVAILABLE")

    override fun get(taskId: String): BridgeTermuxAgentTaskSnapshot? = null
}

class AsyncBridgeTermuxAgentTaskPort(
    private val scope: CoroutineScope,
    private val client: BridgeTermuxDeveloperClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val taskIdFactory: () -> String = { UUID.randomUUID().toString() },
) : BridgeTermuxAgentTaskPort {
    private val tasks = ConcurrentHashMap<String, BridgeTermuxAgentTaskSnapshot>()

    override fun submit(request: BridgeTermuxAgentRequest): BridgeTermuxAgentTaskSnapshot {
        val now = nowEpochMs()
        val taskId = taskIdFactory().also {
            require(TASK_ID.matches(it)) { "TERMUX_AGENT_TASK_ID_INVALID" }
        }
        val queued = BridgeTermuxAgentTaskSnapshot(
            taskId = taskId,
            operation = request.operation,
            state = BridgeTermuxAgentTaskState.QUEUED,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        check(tasks.putIfAbsent(taskId, queued) == null) { "TERMUX_AGENT_TASK_ID_CONFLICT" }
        scope.launch(dispatcher) {
            update(taskId) { current ->
                current.copy(state = BridgeTermuxAgentTaskState.RUNNING, updatedAtEpochMs = nowEpochMs())
            }
            val result = client.execute(request, taskId)
            if (result.isSuccess) {
                val json = result.getOrThrow()
                require(json.length <= MAX_RESULT_CHARS) { "TERMUX_AGENT_RESULT_TOO_LARGE" }
                update(taskId) { current ->
                    current.copy(
                        state = BridgeTermuxAgentTaskState.SUCCEEDED,
                        updatedAtEpochMs = nowEpochMs(),
                        resultJson = json,
                        errorCode = null,
                    )
                }
            } else {
                val code = normalizeError(result.exceptionOrNull())
                update(taskId) { current ->
                    current.copy(
                        state = BridgeTermuxAgentTaskState.FAILED,
                        updatedAtEpochMs = nowEpochMs(),
                        resultJson = null,
                        errorCode = code,
                    )
                }
            }
        }
        return queued
    }

    override fun get(taskId: String): BridgeTermuxAgentTaskSnapshot? {
        if (!TASK_ID.matches(taskId)) return null
        return tasks[taskId]
    }

    private fun update(
        taskId: String,
        transform: (BridgeTermuxAgentTaskSnapshot) -> BridgeTermuxAgentTaskSnapshot,
    ) {
        tasks.computeIfPresent(taskId) { _, current -> transform(current) }
    }

    private fun normalizeError(error: Throwable?): String {
        val message = error?.message.orEmpty()
        return if (ERROR_CODE.matches(message)) message else "TERMUX_AGENT_FAILED"
    }

    private companion object {
        const val MAX_RESULT_CHARS = 2_621_440
        val TASK_ID = Regex("^[A-Za-z0-9._:-]{8,128}$")
        val ERROR_CODE = Regex("^[A-Z0-9_]{3,96}$")
    }
}
