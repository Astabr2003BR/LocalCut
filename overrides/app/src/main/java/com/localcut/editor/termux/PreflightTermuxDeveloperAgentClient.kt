package com.localcut.editor.termux

import com.localcut.editor.bridge.agent.BridgeTermuxAgentRequest
import com.localcut.editor.bridge.agent.BridgeTermuxDeveloperClient

internal class PreflightTermuxDeveloperAgentClient(
    private val delegate: BridgeTermuxDeveloperClient,
    private val engineClient: TermuxEngineClient,
) : BridgeTermuxDeveloperClient {
    override suspend fun execute(
        request: BridgeTermuxAgentRequest,
        taskId: String,
    ): Result<String> {
        val readiness = engineClient.ping()
        if (readiness.isFailure) {
            return Result.failure(
                readiness.exceptionOrNull() ?: IllegalStateException("TERMUX_ENGINE_UNAVAILABLE"),
            )
        }
        return delegate.execute(request, taskId)
    }
}
