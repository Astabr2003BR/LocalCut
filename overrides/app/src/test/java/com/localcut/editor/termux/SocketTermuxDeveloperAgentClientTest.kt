package com.localcut.editor.termux

import com.localcut.editor.bridge.agent.BridgeTermuxAgentRequest
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketTermuxDeveloperAgentClientTest {
    @Test
    fun typedReadUsesAuthenticatedLoopbackRpcAndDoesNotReturnToken() {
        val token = "t".repeat(43)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        var request = ""
        val serverThread = thread(name = "termux-developer-agent-test-server") {
            server.use { listener ->
                listener.accept().use { socket ->
                    request = requireNotNull(socket.getInputStream().bufferedReader().readLine())
                    val requestId = requireNotNull(
                        Regex("\"requestId\":\"([^\"]+)\"").find(request),
                    ).groupValues[1]
                    socket.getOutputStream().bufferedWriter().apply {
                        write(
                            "{\"ok\":true,\"protocolVersion\":1,\"requestId\":\"$requestId\",\"file\":{\"path\":\"README.md\"}}\n",
                        )
                        flush()
                    }
                }
            }
        }
        val client = SocketTermuxDeveloperAgentClient(
            port = server.localPort,
            tokenProvider = { token },
            connectTimeoutMs = 2_000,
            readTimeoutMs = 2_000,
        )

        val result = client.execute(
            "task-12345678",
            BridgeTermuxAgentRequest.ReadWorkspaceFile("README.md"),
        ).getOrThrow()
        serverThread.join(2_000)

        assertFalse(serverThread.isAlive)
        assertTrue(request.contains("\"operation\":\"READ_WORKSPACE_FILE\""))
        assertTrue(request.contains("\"path\":\"README.md\""))
        assertTrue(request.contains("\"token\":\"$token\""))
        assertTrue(result.contains("\"file\""))
        assertFalse(result.contains(token))
    }
}
