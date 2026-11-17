package com.localcut.editor.bridge.transport

import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpBridgeSocketClientImmediateCallbackTest {
    @Test
    fun onOpenBeforeFactoryReturnsStillMarksSocketConnected() {
        val factory = ImmediateOpenFactory()
        val connectedCalls = AtomicInteger(0)
        val client = OkHttpBridgeSocketClient(factory)

        client.connect(
            BridgeSession(
                relayUrl = "wss://relay.example/ws",
                deviceId = "lc_device",
                accessToken = "token",
            ),
            object : BridgeSocketListener {
                override fun onConnected() {
                    connectedCalls.incrementAndGet()
                }

                override fun onTextMessage(text: String) = Unit
                override fun onDisconnected(code: Int?, reason: String?) = Unit
                override fun onFailure(message: String) = Unit
            },
        ).getOrThrow()

        assertEquals(1, connectedCalls.get())
        assertTrue("send must succeed after synchronous onOpen", client.send("ping").isSuccess)
        assertEquals(listOf("ping"), factory.socket.sentTexts)
    }

    private class ImmediateOpenFactory : WebSocket.Factory {
        lateinit var socket: FakeWebSocket

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            val created = FakeWebSocket(request)
            socket = created
            listener.onOpen(
                created,
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(101)
                    .message("Switching Protocols")
                    .build(),
            )
            return created
        }
    }

    private class FakeWebSocket(
        private val initialRequest: Request,
    ) : WebSocket {
        val sentTexts = mutableListOf<String>()
        private var cancelled = false
        private var closed = false

        override fun request(): Request = initialRequest
        override fun queueSize(): Long = 0L

        override fun send(text: String): Boolean {
            if (cancelled || closed) return false
            sentTexts += text
            return true
        }

        override fun send(bytes: ByteString): Boolean = !cancelled && !closed

        override fun close(code: Int, reason: String?): Boolean {
            if (cancelled || closed) return false
            closed = true
            return true
        }

        override fun cancel() {
            cancelled = true
        }
    }
}
