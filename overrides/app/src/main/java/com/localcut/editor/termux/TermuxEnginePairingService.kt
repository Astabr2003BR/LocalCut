package com.localcut.editor.termux

class TermuxEnginePairingService(
    private val client: TermuxEngineClient,
    private val tokenStore: TermuxEngineTokenStore,
) {
    suspend fun pair(pairingCode: String): Result<Unit> {
        val pairing = client.pair(pairingCode)
        if (pairing.isFailure) {
            return Result.failure(
                pairing.exceptionOrNull() ?: IllegalStateException("TERMUX_ENGINE_PAIRING_FAILED"),
            )
        }
        val token = pairing.getOrThrow().token
        return runCatching { tokenStore.save(token) }
    }
}
