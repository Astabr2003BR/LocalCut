package com.localcut.editor.termux

class TermuxEngineUiSession(
    private val pairAction: suspend (String) -> Result<Unit>,
    private val pingAction: suspend () -> Result<Unit>,
    private val statusAction: suspend () -> Result<TermuxEngineStatus>,
    private val capabilitiesAction: suspend () -> Result<TermuxEngineCapabilities>,
    private val clearToken: () -> Unit,
) {
    suspend fun pair(pairingCode: String, current: TermuxEngineUiState): TermuxEngineUiState {
        val pairing = pairAction(pairingCode)
        if (pairing.isFailure) return failure(current, pairing.exceptionOrNull())
        return probe(current.copy(paired = true))
    }

    suspend fun check(current: TermuxEngineUiState): TermuxEngineUiState = probe(current)

    private suspend fun probe(current: TermuxEngineUiState): TermuxEngineUiState {
        val ping = pingAction()
        if (ping.isFailure) return failure(current, ping.exceptionOrNull())

        val status = statusAction()
        if (status.isFailure) return failure(current, status.exceptionOrNull())

        val capabilities = capabilitiesAction()
        if (capabilities.isFailure) return failure(current, capabilities.exceptionOrNull())

        return current.connected(
            engineVersion = status.getOrThrow().engineVersion,
            capabilities = capabilities.getOrThrow().capabilities,
        )
    }

    private fun failure(current: TermuxEngineUiState, error: Throwable?): TermuxEngineUiState {
        val message = error?.message.orEmpty()
        val code = if (ERROR_CODE.matches(message)) message else "TERMUX_ENGINE_UNAVAILABLE"
        if (code == "AUTH_INVALID") {
            clearToken()
            return TermuxEngineUiState.initial(paired = false).failed(code)
        }
        return current.failed(code)
    }

    private companion object {
        val ERROR_CODE = Regex("^[A-Z0-9_]{3,80}$")
    }
}
