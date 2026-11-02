package com.localcut.editor.termux

import com.localcut.editor.bridge.agent.BridgeTermuxAgentRequest
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketTermuxDeveloperAgentClientTest {
    @Test
    fun authenticatedWorkspaceRequestUsesAgentRpcAndDoesNotEchoToken() = runBlocking {
        val token = "t".repeat(43)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        var seen = ""
        val serverThread = thread(name = "termux-developer-agent-test") {
            server.use { listener ->
                listener.accept().use { socket ->
                    seen = requireNotNull(socket.getInputStream().bufferedReader().readLine())
                    val requestId = requireNotNull(
                        Regex("\"requestId\":\"([^\"]+)\"").find(seen),
                    ).groupValues[1]
                    socket.getOutputStream().bufferedWriter().apply {
                        write("{\"ok\":true,\"protocolVersion\":1,\"requestId\":\"$requestId\",\"workspace\":{\"branch\":\"feature/x\"}}\n")
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

        val response = client.execute(
            BridgeTermuxAgentRequest.GetWorkspaceStatus,
            "task-12345678",
        ).getOrThrow()
        serverThread.join(2_000)

        assertFalse(serverThread.isAlive)
        assertTrue(seen.contains("\"operation\":\"GET_WORKSPACE_STATUS\""))
        assertTrue(seen.contains("\"token\":\"$token\""))
        assertFalse(response.contains(token))
        assertTrue(response.contains("feature/x"))
    }

    @Test
    fun commitAndPushEncodeStructuredInputWithoutForce() = runBlocking {
        val token = "t".repeat(43)
        val server = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val seen = mutableListOf<String>()
        val serverThread = thread(name = "termux-git-automation-test") {
            server.use { listener ->
                repeat(2) {
                    listener.accept().use { socket ->
                        val request = requireNotNull(socket.getInputStream().bufferedReader().readLine())
                        synchronized(seen) { seen += request }
                        val requestId = requireNotNull(Regex("\"requestId\":\"([^\"]+)\"").find(request)).groupValues[1]
                        socket.getOutputStream().bufferedWriter().apply {
                            write("{\"ok\":true,\"protocolVersion\":1,\"requestId\":\"$requestId\",\"status\":\"SUCCEEDED\",\"result\":{\"exitCode\":0}}\n")
                            flush()
                        }
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
        client.execute(
            BridgeTermuxAgentRequest.CommitWorkspace(
                expectedBranch = "feature/localcut-bridge-v1",
                message = "feat: auto",
                paths = listOf("README.md"),
            ),
            "task-commit-1234",
        ).getOrThrow()
        client.execute(
            BridgeTermuxAgentRequest.PushWorkspace("feature/localcut-bridge-v1"),
            "task-push-123456",
        ).getOrThrow()
        serverThread.join(2_000)

        assertFalse(serverThread.isAlive)
        assertTrue(seen[0].contains("\"agentOperation\":\"GIT_COMMIT\""))
        assertTrue(seen[0].contains("\"paths\":[\"README.md\"]"))
        assertTrue(seen[1].contains("\"agentOperation\":\"GIT_PUSH\""))
        assertFalse(seen[1].contains("--force"))
    }
}
