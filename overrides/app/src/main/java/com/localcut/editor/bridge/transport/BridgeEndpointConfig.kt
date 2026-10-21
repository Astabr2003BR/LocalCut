package com.localcut.editor.bridge.transport

object BridgeEndpointConfig {
    // Filled with the verified production Railway domain after hosted smoke passes.
    const val HTTPS_BASE_URL: String = ""
    const val WSS_URL: String = ""

    fun isConfigured(): Boolean =
        HTTPS_BASE_URL.startsWith("https://") && WSS_URL.startsWith("wss://")
}
