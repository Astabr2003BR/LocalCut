package com.localcut.editor.termux

import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketTermuxEngineClientTest {
    @Test
    fun pairingThenAuthenticatedPingUsesIssuedToken() = runBlocking {
        val token = "t".repeat(43)
        var storedToken: String? = null
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val serverThread = thread(name = "termux-engine-test-server") {
            server.use { listener ->
                listener.accept().use { socket ->
                    val request = requireNotNull(socket.getInputStream().bufferedReader().readLine())
                    requests += request
                    val requestId = requestIdFrom(request)
                    socket.getOutputStream().bufferedWriter().apply {
                        write("{\"ok\":true,\"protocolVersion\":1,\"requestId\":\"$requestId\",\"token\":\"$token\"}\n")
                        flush()
                    }
                }
                listener.accept().use { socket ->
                    val request = requireNotNull(socket.getInputStream().bufferedReader().readLine())
                    requests += request
                    val requestId = requestIdFrom(request)
                    socket.getOutputStream().bufferedWriter().apply {
                        write("{\"ok\":true,\"protocolVersion\":1,\"requestId\":\"$requestId\",\"status\":\"READY\"}\n")
                        flush()
                    }
                }
            }
        }

        val client = SocketTermuxEngineClient(
            port = server.localPort,
            tokenProvider = { storedToken },
            connectTimeoutMs = 2_000,
            readTimeoutMs = 2_000,
        )
        val pairing = client.pair("2345-6789-abcd").getOrThrow()
        storedToken = pairing.token
        val ping = client.ping().getOrThrow()
        serverThread.join(2_000)

        assertFalse(serverThread.isAlive)
        assertEquals("READY", ping.status)
        assertEquals(2, requests.size)
        assertTrue(requests[0].contains("\"operation\":\"PAIR\""))
        assertTrue(requests[0].contains("\"pairingCode\":\"23456789ABCD\""))
        assertFalse(requests[0].contains("\"token\""))
        assertTrue(requests[1].contains("\"operation\":\"PING\""))
        assertTrue(requests[1].contains("\"token\":\"$token\""))
    }

    private fun requestIdFrom(request: String): String =
        requireNotNull(Regex("\"requestId\":\"([^\"]+)\"").find(request)) {
            "requestId missing from test request"
        }.groupValues[1]
}
