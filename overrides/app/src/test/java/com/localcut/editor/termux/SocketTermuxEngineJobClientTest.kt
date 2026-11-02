package com.localcut.editor.termux

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketTermuxEngineJobClientTest {
    @Test
    fun stageMediaStreamsExactBytesAndVerifiesDigest() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val token = "a".repeat(43)
        val payload = "abcde".toByteArray()
        val sha = sha256(payload)
        val worker = thread {
            server.use { ss ->
                ss.accept().use { socket ->
                    val input = socket.getInputStream()
                    val header = readLine(input)
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
                    val requestId = requestIdFrom(header)
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

    @Test
    fun downloadGeneratedMediaStreamsBinaryBodyAndPublishesOnlyAfterDigestMatches() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val token = "b".repeat(43)
        val payload = byteArrayOf(0x00, 0x01, 0x7f, 0xff.toByte(), 0x10, 0x00)
        val sha = sha256(payload)
        val worker = thread {
            server.use { ss ->
                ss.accept().use { socket ->
                    val input = socket.getInputStream()
                    val request = readLine(input)
                    assertTrue(request.contains("\"operation\":\"GET_JOB\""))
                    assertTrue(request.contains("\"includeGeneratedMedia\":true"))
                    val requestId = requestIdFrom(request)
                    val response = """{"ok":true,"protocolVersion":1,"requestId":"$requestId","job":{"id":"123e4567-e89b-12d3-a456-426614174000","type":"ANALYZE_MEDIA","status":"SUCCEEDED","progress":100,"output":null,"error":null},"media":{"sizeBytes":${payload.size},"sha256":"$sha","mimeType":"video/mp4"},"contentLength":${payload.size},"sha256":"$sha","binaryBody":true}""" + "\n"
                    socket.getOutputStream().apply {
                        write(response.toByteArray())
                        write(payload)
                        flush()
                    }
                }
            }
        }

        val root = Files.createTempDirectory("localcut-download-test").toFile()
        try {
            val destination = root.resolve("reverse.mp4")
            val client = SocketTermuxEngineJobClient(
                host = "127.0.0.1",
                port = server.localPort,
                tokenProvider = { token },
            )
            val result = client.downloadGeneratedMedia(
                "123e4567-e89b-12d3-a456-426614174000",
                destination,
            ).getOrThrow()

            assertTrue(destination.isFile)
            assertTrue(destination.readBytes().contentEquals(payload))
            assertEquals(payload.size.toLong(), result.media.sizeBytes)
            assertEquals(sha, result.media.sha256)
            assertEquals("video/mp4", result.media.mimeType)
            assertEquals(destination, result.file)
            assertEquals(TermuxEngineJobStatus.SUCCEEDED, result.job.status)
            worker.join(2_000L)
            assertFalse(worker.isAlive)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun downloadGeneratedMediaDigestMismatchDoesNotPublishDestination() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val token = "c".repeat(43)
        val payload = "corrupted".toByteArray()
        val declaredSha = sha256("expected".toByteArray())
        val worker = thread {
            server.use { ss ->
                ss.accept().use { socket ->
                    val request = readLine(socket.getInputStream())
                    val requestId = requestIdFrom(request)
                    val response = """{"ok":true,"protocolVersion":1,"requestId":"$requestId","job":{"id":"123e4567-e89b-12d3-a456-426614174000","type":"ANALYZE_MEDIA","status":"SUCCEEDED","progress":100,"output":null,"error":null},"media":{"sizeBytes":${payload.size},"sha256":"$declaredSha","mimeType":"video/mp4"},"contentLength":${payload.size},"sha256":"$declaredSha","binaryBody":true}""" + "\n"
                    socket.getOutputStream().apply {
                        write(response.toByteArray())
                        write(payload)
                        flush()
                    }
                }
            }
        }

        val root = Files.createTempDirectory("localcut-download-fail-test").toFile()
        try {
            val destination = root.resolve("reverse.mp4")
            val client = SocketTermuxEngineJobClient(
                host = "127.0.0.1",
                port = server.localPort,
                tokenProvider = { token },
            )
            val result = client.downloadGeneratedMedia(
                "123e4567-e89b-12d3-a456-426614174000",
                destination,
            )

            assertTrue(result.isFailure)
            assertFalse(destination.exists())
            assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
            worker.join(2_000L)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun readLine(input: java.io.InputStream): String = buildString {
        while (true) {
            val next = input.read()
            check(next != -1)
            if (next == '\n'.code) break
            append(next.toChar())
        }
    }

    private fun requestIdFrom(request: String): String =
        Regex("\\\"requestId\\\":\\\"([^\\\"]+)\\\"").find(request)!!.groupValues[1]

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
