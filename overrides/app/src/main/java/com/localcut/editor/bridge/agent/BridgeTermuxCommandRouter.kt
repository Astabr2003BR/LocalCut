package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.protocol.BridgeCapability
import com.localcut.editor.bridge.protocol.BridgeCommandEnvelope
import com.localcut.editor.bridge.protocol.BridgeErrorCode
import com.localcut.editor.bridge.protocol.BridgeResultEnvelope
import com.localcut.editor.data.project.JNull
import com.localcut.editor.data.project.JObject
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asArray
import com.localcut.editor.data.project.asInt
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required

class BridgeTermuxCommandRouter(
    private val port: BridgeTermuxAgentTaskPort,
    private val activeProjectContextProvider: () -> BridgeActiveProjectContext?,
) {
    fun handles(command: BridgeCommandEnvelope): Boolean = runCatching {
        val root = MiniJson.parse(command.payloadJson).asObject()
        root.fields.containsKey(MARKER)
    }.getOrDefault(false)

    fun route(command: BridgeCommandEnvelope): BridgeResultEnvelope {
        val context = activeProjectContextProvider()
            ?: return error(command, BridgeErrorCode.PROJECT_NOT_FOUND, retryable = false)
        if (command.projectId != context.projectId) {
            return error(command, BridgeErrorCode.PROJECT_NOT_FOUND, retryable = false)
        }

        return try {
            val payload = MiniJson.parse(command.payloadJson).asObject().required(MARKER).asObject()
            require(payload.required("version").asInt() == VERSION) { "TERMUX_BRIDGE_VERSION_INVALID" }
            when (val operation = payload.required("operation").asString()) {
                "GET_WORKSPACE_STATUS" -> {
                    requireReadCarrier(command)
                    queued(command, context, port.submit(BridgeTermuxAgentRequest.GetWorkspaceStatus))
                }
                "LIST_WORKSPACE_FILES" -> {
                    requireReadCarrier(command)
                    val path = payload.optionalString("path") ?: ""
                    val limit = payload.optionalInt("limit") ?: 200
                    require(limit in 1..500) { "WORKSPACE_LIMIT_INVALID" }
                    queued(
                        command,
                        context,
                        port.submit(BridgeTermuxAgentRequest.ListWorkspaceFiles(path, limit)),
                    )
                }
                "READ_WORKSPACE_FILE" -> {
                    requireReadCarrier(command)
                    queued(
                        command,
                        context,
                        port.submit(BridgeTermuxAgentRequest.ReadWorkspaceFile(payload.requiredString("path"))),
                    )
                }
                "APPLY_TEXT_PATCH" -> {
                    requireWriteCarrier(command)
                    queued(
                        command,
                        context,
                        port.submit(
                            BridgeTermuxAgentRequest.ApplyTextPatch(
                                path = payload.requiredString("path"),
                                expectedSha256 = payload.requiredString("expectedSha256"),
                                oldText = payload.requiredString("oldText"),
                                newText = payload.required("newText").asString(),
                            ),
                        ),
                    )
                }
                "RUN_AGENT_OPERATION" -> {
                    requireWriteCarrier(command)
                    queued(
                        command,
                        context,
                        port.submit(
                            BridgeTermuxAgentRequest.RunAgentOperation(
                                payload.requiredString("agentOperation"),
                            ),
                        ),
                    )
                }
                "COMMIT_WORKSPACE" -> {
                    requireWriteCarrier(command)
                    val paths = payload.required("paths").asArray().values.map { it.asString() }
                    require(paths.isNotEmpty() && paths.size <= 50) { "AGENT_PATHS_INVALID" }
                    queued(
                        command,
                        context,
                        port.submit(
                            BridgeTermuxAgentRequest.CommitWorkspace(
                                expectedBranch = payload.requiredString("expectedBranch"),
                                message = payload.requiredString("message"),
                                paths = paths,
                            ),
                        ),
                    )
                }
                "PUSH_WORKSPACE" -> {
                    requireWriteCarrier(command)
                    queued(
                        command,
                        context,
                        port.submit(
                            BridgeTermuxAgentRequest.PushWorkspace(
                                expectedBranch = payload.requiredString("expectedBranch"),
                            ),
                        ),
                    )
                }
                "GET_TASK" -> {
                    requireReadCarrier(command)
                    val taskId = payload.requiredString("taskId")
                    val task = port.get(taskId)
                        ?: return error(command, BridgeErrorCode.INVALID_ARGUMENT, retryable = false)
                    success(command, context, taskJson(task))
                }
                else -> throw IllegalArgumentException("TERMUX_BRIDGE_OPERATION_NOT_ALLOWED:$operation")
            }
        } catch (error: IllegalArgumentException) {
            error(command, BridgeErrorCode.INVALID_ARGUMENT, retryable = false)
        } catch (error: IllegalStateException) {
            val message = error.message.orEmpty()
            if (message == "TERMUX_AGENT_UNAVAILABLE") {
                error(command, BridgeErrorCode.DEVICE_OFFLINE, retryable = true)
            } else {
                error(command, BridgeErrorCode.INTERNAL_ERROR, retryable = true)
            }
        } catch (_: Throwable) {
            error(command, BridgeErrorCode.INVALID_ARGUMENT, retryable = false)
        }
    }

    private fun requireReadCarrier(command: BridgeCommandEnvelope) {
        require(command.capability == BridgeCapability.GET_DEVICE_STATUS) {
            "TERMUX_BRIDGE_READ_CARRIER_INVALID"
        }
    }

    private fun requireWriteCarrier(command: BridgeCommandEnvelope) {
        require(command.capability == BridgeCapability.CANCEL_EXPORT) {
            "TERMUX_BRIDGE_WRITE_CARRIER_INVALID"
        }
    }

    private fun queued(
        command: BridgeCommandEnvelope,
        context: BridgeActiveProjectContext,
        task: BridgeTermuxAgentTaskSnapshot,
    ): BridgeResultEnvelope = success(command, context, taskJson(task))

    private fun success(
        command: BridgeCommandEnvelope,
        context: BridgeActiveProjectContext,
        resultJson: String,
    ) = BridgeResultEnvelope(
        messageId = command.messageId,
        success = true,
        code = null,
        projectRevision = context.revision,
        resultJson = resultJson,
        retryable = false,
    )

    private fun error(
        command: BridgeCommandEnvelope,
        code: BridgeErrorCode,
        retryable: Boolean,
    ) = BridgeResultEnvelope(
        messageId = command.messageId,
        success = false,
        code = code,
        projectRevision = activeProjectContextProvider()?.revision,
        resultJson = "{}",
        retryable = retryable,
    )

    private fun taskJson(task: BridgeTermuxAgentTaskSnapshot): String = buildString {
        append('{')
        append("\"taskId\":"); append(jsonString(task.taskId))
        append(",\"operation\":"); append(jsonString(task.operation))
        append(",\"state\":"); append(jsonString(task.state.name))
        append(",\"createdAtEpochMs\":"); append(task.createdAtEpochMs)
        append(",\"updatedAtEpochMs\":"); append(task.updatedAtEpochMs)
        append(",\"result\":")
        append(task.resultJson ?: "null")
        append(",\"errorCode\":")
        append(task.errorCode?.let(::jsonString) ?: "null")
        append('}')
    }

    private fun JObject.requiredString(key: String): String = required(key).asString().also {
        require(it.isNotBlank()) { "$key must not be blank" }
    }

    private fun JObject.optionalString(key: String): String? = when (val value = optional(key)) {
        null, JNull -> null
        else -> value.asString()
    }

    private fun JObject.optionalInt(key: String): Int? = when (val value = optional(key)) {
        null, JNull -> null
        else -> value.asInt()
    }

    private companion object {
        const val MARKER = "localcutTermux"
        const val VERSION = 1
    }
}
