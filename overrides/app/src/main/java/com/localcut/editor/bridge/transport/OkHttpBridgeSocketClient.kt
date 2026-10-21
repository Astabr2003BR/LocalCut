package com.localcut.editor.bridge.transport

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class OkHttpBridgeSocketClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : BridgeSocketClient {
    private val lock = Any()
    private var socket: WebSocket? = null
    private var connected = false
    private var listener: BridgeSocketListener? = null

    override fun connect(session: BridgeSession, listener: BridgeSocketListener): Result<Unit> = runCatching {
        synchronized(lock) {
            if (socket != null) throw IllegalStateException("bridge socket already active")
            this.listener = listener
            connected = false
        }

        val request = Request.Builder()
            .url(session.relayUrl)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("X-LocalCut-Device-Id", session.deviceId)
            .header("X-LocalCut-Protocol-Version", session.protocolVersion.toString())
            .build()

        val created = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (socket !== webSocket) return
                    connected = true
                }
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (isCurrent(webSocket)) listener.onTextMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (isCurrent(webSocket)) webSocket.close(code, reason.take(120))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!clearIfCurrent(webSocket)) return
                listener.onDisconnected(code, reason.take(240))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!clearIfCurrent(webSocket)) return
                listener.onFailure(t.message?.take(240) ?: "bridge websocket failed")
            }
        })

        synchronized(lock) {
            if (socket != null) {
                created.cancel()
                throw IllegalStateException("bridge socket already active")
            }
            socket = created
        }
    }

    override fun send(text: String): Result<Unit> = runCatching {
        require(text.isNotBlank()) { "bridge message must not be blank" }
        val active = synchronized(lock) {
            if (!connected) null else socket
        } ?: throw IllegalStateException("bridge socket is not connected")
        check(active.send(text)) { "bridge websocket send rejected" }
    }

    override fun disconnect(): Result<Unit> = runCatching {
        val active = synchronized(lock) {
            connected = false
            listener = null
            socket.also { socket = null }
        }
        if (active != null && !active.close(NORMAL_CLOSE_CODE, "local_disconnect")) {
            active.cancel()
        }
    }

    private fun isCurrent(webSocket: WebSocket): Boolean = synchronized(lock) { socket === webSocket }

    private fun clearIfCurrent(webSocket: WebSocket): Boolean = synchronized(lock) {
        if (socket !== webSocket) return@synchronized false
        socket = null
        connected = false
        listener = null
        true
    }

    private companion object {
        const val NORMAL_CLOSE_CODE = 1000
    }
}
