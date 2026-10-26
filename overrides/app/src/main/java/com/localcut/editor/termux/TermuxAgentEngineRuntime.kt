package com.localcut.editor.termux

import android.content.Context

class TermuxAgentEngineRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext

    val tokenStore: TermuxEngineTokenStore = AndroidKeystoreTermuxEngineTokenStore(appContext)
    val client: TermuxEngineClient = SocketTermuxEngineClient(tokenProvider = tokenStore::load)

    companion object {
        @Volatile
        private var instance: TermuxAgentEngineRuntime? = null

        fun get(context: Context): TermuxAgentEngineRuntime = instance ?: synchronized(this) {
            instance ?: TermuxAgentEngineRuntime(context.applicationContext).also { instance = it }
        }
    }
}
