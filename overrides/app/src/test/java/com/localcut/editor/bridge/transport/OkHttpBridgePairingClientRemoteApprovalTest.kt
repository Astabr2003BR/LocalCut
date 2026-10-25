package com.localcut.editor.bridge.transport

import java.util.concurrent.atomic.AtomicReference
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpBridgePairingClientRemoteApprovalTest {
    @Test
    fun remoteApprovalUsesAuthenticatedPostWithoutTokenInUrl() {
        val captured = AtomicReference<Request>()
        val http = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                captured.set(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{\"approved\":true}".toResponseBody(JSON))
                    .build()
            })
            .build()
        val client = OkHttpBridgePairingClient(
            baseUrl = "https://bridge.example",
            client = http,
        )

        client.approveRemoteControl(
            requestId = "remote_request_1234567890",
            accessToken = "secret-access-token",
        ).getOrThrow()

        val request = captured.get()
        assertEquals("POST", request.method)
        assertEquals("https://bridge.example/v1/remote/device-approve", request.url.toString())
        assertNull(request.url.query)
        assertEquals("Bearer secret-access-token", request.header("Authorization"))

        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertEquals("{\"requestId\":\"remote_request_1234567890\"}", buffer.readUtf8())
        assertTrue(request.header("Authorization")!!.startsWith("Bearer "))
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
