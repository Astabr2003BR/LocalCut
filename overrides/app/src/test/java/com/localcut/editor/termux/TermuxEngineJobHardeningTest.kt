package com.localcut.editor.termux

import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asObject
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEngineJobHardeningTest {
    @Test
    fun jobResponseRejectsProtocolMismatch() {
        val job = """{"id":"123e4567-e89b-12d3-a456-426614174000","type":"ANALYZE_MEDIA","status":"SUCCEEDED","progress":100,"output":null,"error":null}"""
        val root = MiniJson.parse("""{"protocolVersion":2,"job":$job}""").asObject()
        val error = runCatching { TermuxEngineJobResponseParser.parseJob(root) }.exceptionOrNull()
        assertEquals("TERMUX_ENGINE_PROTOCOL_MISMATCH", error?.message)
    }

    @Test
    fun stageMediaRejectsServerSizeMismatchEvenWhenDigestMatches() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val token = "a".repeat(43)
        val payload = "abcde".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it) }
        val worker = thread {
            server.use { ss ->
                ss.accept().use { socket ->
                    val input = socket.getInputStream()
                    val header = buildString {
                        while (true) {
                            val next = input.read()
                            check(next != -1)
                            if (next == '\n'.code) break
                            append(next.toChar())
                        }
                    }
                    val body = ByteArray(5)
                    var offset = 0
                    while (offset < body.size) {
                        val count = input.read(body, offset, body.size - offset)
                        check(count > 0)
                        offset += count
                    }
                    val requestId = Regex("\\\"requestId\\\":\\\"([^\\\"]+)\\\"")
                        .find(header)!!.groupValues[1]
                    val response = """{"ok":true,"protocolVersion":1,"requestId":"$requestId","job":{"id":"123e4567-e89b-12d3-a456-426614174000","type":"ANALYZE_MEDIA","status":"SUCCEEDED","progress":100,"output":{"analysis":{"formatName":"fake","durationUs":2000000,"sizeBytes":5,"bitRate":64000,"streams":[]}},"error":null},"transfer":{"sizeBytes":6,"sha256":"$sha"}}""" + "\n"
                    socket.getOutputStream().write(response.toByteArray())
                    socket.getOutputStream().flush()
                }
            }
        }
        val client = SocketTermuxEngineJobClient(
            host = "127.0.0.1",
            port = server.localPort,
            tokenProvider = { token },
        )
        val result = client.stageMedia(
            "123e4567-e89b-12d3-a456-426614174000",
            TermuxEngineMediaSource(5L) { ByteArrayInputStream(payload) },
        )
        assertTrue(result.isFailure)
        assertEquals("TERMUX_ENGINE_TRANSFER_SIZE_MISMATCH", result.exceptionOrNull()?.message)
        worker.join(2_000L)
    }
}
