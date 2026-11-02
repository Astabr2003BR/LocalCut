package com.localcut.editor.bridge.transport

data class BridgeSession(
    val relayUrl: String,
    val deviceId: String,
    val accessToken: String,
    val protocolVersion: Int = 1,
) {
    init {
        require(relayUrl.startsWith("wss://")) { "relayUrl must use wss://" }
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        require(protocolVersion == 1) { "unsupported protocolVersion" }
    }

    override fun toString(): String =
        "BridgeSession(relayUrl=$relayUrl, deviceId=$deviceId, accessToken=[redacted], protocolVersion=$protocolVersion)"
}

interface BridgeSocketListener {
    fun onConnected()
    fun onTextMessage(text: String)
    fun onDisconnected(code: Int?, reason: String?)
    fun onFailure(message: String)
}

interface BridgeSocketClient {
    fun connect(session: BridgeSession, listener: BridgeSocketListener): Result<Unit>
    fun send(text: String): Result<Unit>
    fun disconnect(): Result<Unit>
}
