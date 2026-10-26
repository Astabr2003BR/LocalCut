package com.localcut.editor.termux

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketTermuxEngineJobClientTest {
    @Test
    fun stageMediaStreamsExactBytesAndVerifiesDigest() = runBlocking {
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
                    assertTrue(header.contains("\"operation\":\"STAGE_MEDIA\""))
                    assertTrue(header.contains("\"contentLength\":5"))
                    val body = ByteArray(5)
                    var offset = 0
                    while (offset < body.size) {
                        val count = input.read(body, offset, body.size - offset)
                        check(count > 0)
                        offset += count
                    }
                    assertTrue(body.contentEquals(payload))
                    val requestId = Regex("\\\"requestId\\\":\\\"([^\\\"]+)\\\"")
                        .find(header)!!.groupValues[1]
                    val response = """{"ok":true,"protocolVersion":1,"requestId":"$requestId","job":{"id":"123e4567-e89b-12d3-a456-426614174000","type":"ANALYZE_MEDIA","status":"SUCCEEDED","progress":100,"output":{"analysis":{"formatName":"fake","durationUs":2000000,"sizeBytes":5,"bitRate":64000,"streams":[]}},"error":null},"transfer":{"sizeBytes":5,"sha256":"$sha"}}""" + "\n"
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
        ).getOrThrow()
        assertEquals(sha, result.sha256)
        assertEquals(TermuxEngineJobStatus.SUCCEEDED, result.job.status)
        assertEquals(2_000_000L, result.job.analysis?.durationUs)
        worker.join(2_000L)
    }
}
