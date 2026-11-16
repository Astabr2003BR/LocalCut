package com.localcut.editor.mobile

import java.io.File
import java.net.URI
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/** Explicit one-time relay package descriptor. No arbitrary URL, redirect or bearer token storage. */
data class PortableStudioRelay(val url: String, val sha256: String, val bytes: Long) {
    init {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == HOST && uri.port == -1 &&
            uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.matches(Regex("/v1/mobile-media/[A-Za-z0-9_-]{43}"))) { "PACKAGE_RELAY_URL_INVALID" }
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "PACKAGE_RELAY_HASH_INVALID" }
        require(bytes in 1L..MAX_BYTES) { "PACKAGE_RELAY_SIZE_INVALID" }
    }

    companion object {
        const val HOST = "localcut-bridge-v1.onrender.com"
        const val MAX_BYTES = 20L * 1024 * 1024
        const val MIME = "application/vnd.localcut.package"
        const val EXTRA_URL = "localcut_package_relay_url"
        const val EXTRA_HASH = "localcut_package_relay_hash"
        const val EXTRA_BYTES = "localcut_package_relay_bytes"
    }
}

internal object PortableStudioRelayReceiver {
    // Caller runs on IO; DNS is resolved once and only public addresses are used for TLS.
    fun receive(request: PortableStudioRelay, directory: File, checkCancelled: () -> Unit): PortableStudioStager.Staged {
        checkCancelled()
        val addresses = InetAddress.getAllByName(PortableStudioRelay.HOST).toList()
        RemoteMediaUrlPolicy.requirePublicAddresses(addresses).getOrThrow()
        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .dns(Dns { host -> require(host == PortableStudioRelay.HOST); addresses })
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val http = Request.Builder().url(request.url)
            .header("Accept", PortableStudioRelay.MIME)
            .header("Accept-Encoding", "identity").get().build()
        checkCancelled()
        return client.newCall(http).execute().use { response ->
            checkCancelled()
            require(response.code == 200 && response.header("Content-Range") == null) { "PACKAGE_RELAY_HTTP_INVALID" }
            require(response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() == PortableStudioRelay.MIME) { "PACKAGE_RELAY_MIME_INVALID" }
            require(response.header("Content-Encoding")?.trim()?.lowercase().let { it == null || it == "identity" }) { "PACKAGE_RELAY_ENCODING_INVALID" }
            val body = response.body
            require(response.header("Content-Length")?.toLongOrNull() == request.bytes && body.contentLength() == request.bytes) { "PACKAGE_RELAY_LENGTH_MISMATCH" }
            PortableStudioStager.stage(directory, { body.byteStream() }, checkCancelled,
                expectedArchive = request)
        }
    }
}
