package com.localcut.editor.termux

import android.content.Context

class TermuxAgentEngineRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext

    val tokenStore: TermuxEngineTokenStore = AndroidKeystoreTermuxEngineTokenStore(appContext)
    val client: TermuxEngineClient = SocketTermuxEngineClient(tokenProvider = tokenStore::load)

    suspend fun pair(pairingCode: String): Result<TermuxEnginePing> {
        val pairingResult = client.pair(pairingCode)
        if (pairingResult.isFailure) {
            return Result.failure(pairingResult.exceptionOrNull() ?: IllegalStateException("TERMUX_ENGINE_PAIRING_FAILED"))
        }
        val pairing = pairingResult.getOrThrow()
        val saveResult = runCatching { tokenStore.save(pairing.token) }
        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: IllegalStateException("TERMUX_ENGINE_TOKEN_PERSIST_FAILED"))
        }
        return client.ping()
    }

    companion object {
        @Volatile
        private var instance: TermuxAgentEngineRuntime? = null

        fun get(context: Context): TermuxAgentEngineRuntime = instance ?: synchronized(this) {
            instance ?: TermuxAgentEngineRuntime(context.applicationContext).also { instance = it }
        }
    }
}
