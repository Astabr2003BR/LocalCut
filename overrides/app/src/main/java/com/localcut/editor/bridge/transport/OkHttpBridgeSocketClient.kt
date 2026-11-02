package com.localcut.editor.bridge.transport

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class OkHttpBridgeSocketClient(
    private val webSocketFactory: WebSocket.Factory = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : BridgeSocketClient {
    private val lock = Any()
    private var socket: WebSocket? = null
    private var connected = false
    private var nextAttemptId = 0L
    private var activeAttemptId: Long? = null

    override fun connect(session: BridgeSession, listener: BridgeSocketListener): Result<Unit> = runCatching {
        val attemptId = synchronized(lock) {
            if (activeAttemptId != null) throw IllegalStateException("bridge socket already active")
            nextAttemptId = if (nextAttemptId == Long.MAX_VALUE) 1L else nextAttemptId + 1L
            activeAttemptId = nextAttemptId
            socket = null
            connected = false
            nextAttemptId
        }

        val request = Request.Builder()
            .url(session.relayUrl)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("X-LocalCut-Device-Id", session.deviceId)
            .header("X-LocalCut-Protocol-Version", session.protocolVersion.toString())
            .build()

        val created = try {
            webSocketFactory.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!bindOpenIfCurrent(attemptId, webSocket)) return
                    listener.onConnected()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (isCurrent(attemptId, webSocket)) listener.onTextMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (isCurrent(attemptId, webSocket)) webSocket.close(code, reason.take(120))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!clearIfCurrent(attemptId, webSocket)) return
                    listener.onDisconnected(code, reason.take(240))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!clearIfCurrent(attemptId, webSocket)) return
                    if (response?.code == HTTP_UNAUTHORIZED) {
                        listener.onDisconnected(HTTP_UNAUTHORIZED, response.message.take(240))
                    } else {
                        listener.onFailure(t.message?.take(240) ?: "bridge websocket failed")
                    }
                }
            })
        } catch (error: Throwable) {
            clearAttemptIfCurrent(attemptId)
            throw error
        }

        var cancelCreated = false
        var stateError: IllegalStateException? = null
        synchronized(lock) {
            when {
                activeAttemptId != attemptId -> cancelCreated = true
                socket == null -> socket = created
                socket !== created -> {
                    activeAttemptId = null
                    socket = null
                    connected = false
                    cancelCreated = true
                    stateError = IllegalStateException("bridge websocket callback returned a different socket")
                }
            }
        }
        if (cancelCreated) created.cancel()
        stateError?.let { throw it }
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
            activeAttemptId = null
            socket.also { socket = null }
        }
        if (active != null && !active.close(NORMAL_CLOSE_CODE, "local_disconnect")) {
            active.cancel()
        }
    }

    private fun bindOpenIfCurrent(attemptId: Long, webSocket: WebSocket): Boolean = synchronized(lock) {
        if (activeAttemptId != attemptId) return@synchronized false
        if (socket != null && socket !== webSocket) return@synchronized false
        socket = webSocket
        connected = true
        true
    }

    private fun isCurrent(attemptId: Long, webSocket: WebSocket): Boolean = synchronized(lock) {
        activeAttemptId == attemptId && socket === webSocket
    }

    private fun clearIfCurrent(attemptId: Long, webSocket: WebSocket): Boolean = synchronized(lock) {
        if (activeAttemptId != attemptId) return@synchronized false
        if (socket != null && socket !== webSocket) return@synchronized false
        activeAttemptId = null
        socket = null
        connected = false
        true
    }

    private fun clearAttemptIfCurrent(attemptId: Long) {
        synchronized(lock) {
            if (activeAttemptId != attemptId) return
            activeAttemptId = null
            socket = null
            connected = false
        }
    }

    private companion object {
        const val NORMAL_CLOSE_CODE = 1000
        const val HTTP_UNAUTHORIZED = 401
    }
}
