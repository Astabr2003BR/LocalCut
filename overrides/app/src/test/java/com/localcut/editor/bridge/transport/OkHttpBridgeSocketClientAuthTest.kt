package com.localcut.editor.bridge.transport

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpBridgeSocketClientAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var trustedClient: OkHttpClient

    @Before
    fun setUp() {
        val serverCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCertificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(serverCertificate.certificate)
            .build()

        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start()
        trustedClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    @Test
    fun handshake401IsReportedAsAuthenticationDisconnectInsteadOfGenericTransportFailure() {
        server.enqueue(MockResponse.Builder().code(401).body("Unauthorized").build())
        val disconnectedCode = AtomicInteger(-1)
        val failure = AtomicReference<String?>(null)
        val callback = CountDownLatch(1)
        val client = OkHttpBridgeSocketClient(trustedClient)
        val wssUrl = server.url("/ws").toString().replaceFirst("https://", "wss://")

        client.connect(
            BridgeSession(wssUrl, "lc_device", "rejected-token"),
            object : BridgeSocketListener {
                override fun onConnected() = Unit
                override fun onTextMessage(text: String) = Unit

                override fun onDisconnected(code: Int?, reason: String?) {
                    disconnectedCode.set(code ?: -1)
                    callback.countDown()
                }

                override fun onFailure(message: String) {
                    failure.set(message)
                    callback.countDown()
                }
            },
        ).getOrThrow()

        assertTrue("websocket callback timed out", callback.await(5, TimeUnit.SECONDS))
        assertEquals(401, disconnectedCode.get())
        assertNull("401 must be classified separately from DNS/transport failures", failure.get())
    }
}
