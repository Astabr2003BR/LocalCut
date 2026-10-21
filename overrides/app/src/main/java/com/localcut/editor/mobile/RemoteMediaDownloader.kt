package com.localcut.editor.mobile

import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface RemoteMediaAddressResolver {
    fun resolve(host: String): List<InetAddress>
}

data class DownloadedRemoteMedia(
    val file: File,
    val declaredContentType: String?,
    val byteCount: Long,
)

class RemoteMediaDownloader(
    private val cacheDir: File,
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val addressResolver: RemoteMediaAddressResolver = RemoteMediaAddressResolver { host ->
        InetAddress.getAllByName(host).toList()
    },
    private val addressValidator: (List<InetAddress>) -> Result<Unit> = RemoteMediaUrlPolicy::requirePublicAddresses,
    private val provisionalLimitBytes: (String?) -> Long = ::defaultProvisionalLimitBytes,
    private val maxRedirects: Int = 3,
) {
    suspend fun download(rawUrl: String): Result<DownloadedRemoteMedia> = withContext(Dispatchers.IO) {
        runCatching { downloadBlocking(rawUrl) }
    }

    private fun downloadBlocking(rawUrl: String): DownloadedRemoteMedia {
        require(maxRedirects >= 0) { "REMOTE_MEDIA_REDIRECT_LIMIT_INVALID" }
        var current = RemoteMediaUrlPolicy.parseAndValidate(rawUrl).getOrThrow()
        var redirects = 0

        while (true) {
            val host = current.host ?: error("REMOTE_MEDIA_URL_HOST_INVALID")
            val addresses = addressResolver.resolve(host)
            addressValidator(addresses).getOrThrow()

            val pinnedDns = Dns { hostname ->
                if (hostname.equals(host, ignoreCase = true)) addresses
                else addressResolver.resolve(hostname).also { addressValidator(it).getOrThrow() }
            }
            val client = baseClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(pinnedDns)
                .build()
            val request = Request.Builder().url(current.toASCIIString()).get().build()

            client.newCall(request).execute().use { response ->
                if (response.code in REDIRECT_CODES) {
                    require(redirects < maxRedirects) { "REMOTE_MEDIA_TOO_MANY_REDIRECTS" }
                    val location = response.header("Location")
                        ?: error("REMOTE_MEDIA_REDIRECT_LOCATION_MISSING")
                    current = resolveRedirect(current, location)
                    redirects += 1
                } else {
                    require(response.isSuccessful) { "REMOTE_MEDIA_HTTP_${response.code}" }
                    val body = response.body ?: error("REMOTE_MEDIA_BODY_MISSING")
                    val declaredContentType = normalizeContentType(response.header("Content-Type"))
                    val limit = provisionalLimitBytes(declaredContentType)
                    require(limit > 0L) { "REMOTE_MEDIA_LIMIT_INVALID" }
                    val contentLength = body.contentLength()
                    require(contentLength < 0L || contentLength <= limit) { "REMOTE_MEDIA_TOO_LARGE" }

                    val remoteDir = File(cacheDir, REMOTE_DIR_NAME).apply { mkdirs() }
                    val partial = File(remoteDir, "${UUID.randomUUID()}.part")
                    try {
                        var total = 0L
                        body.byteStream().use { input ->
                            partial.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    total += read.toLong()
                                    require(total <= limit) { "REMOTE_MEDIA_TOO_LARGE" }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        require(total > 0L) { "REMOTE_MEDIA_EMPTY" }
                        return DownloadedRemoteMedia(
                            file = partial,
                            declaredContentType = declaredContentType,
                            byteCount = total,
                        )
                    } catch (error: Throwable) {
                        partial.delete()
                        deleteDirectoryIfEmpty(remoteDir)
                        throw error
                    }
                }
            }
        }
    }

    private fun resolveRedirect(base: URI, location: String): URI {
        val resolved = base.resolve(location.trim())
        return RemoteMediaUrlPolicy.parseAndValidate(resolved.toASCIIString()).getOrThrow()
    }

    private fun deleteDirectoryIfEmpty(directory: File) {
        if (directory.isDirectory && directory.listFiles().orEmpty().isEmpty()) {
            directory.delete()
        }
    }

    private companion object {
        const val REMOTE_DIR_NAME = "remote-import"
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        fun normalizeContentType(value: String?): String? = value
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }

        fun defaultProvisionalLimitBytes(contentType: String?): Long = when {
            contentType?.startsWith("image/") == true -> RemoteMediaValidator.limitBytes(RemoteMediaKind.IMAGE)
            contentType?.startsWith("audio/") == true -> RemoteMediaValidator.limitBytes(RemoteMediaKind.AUDIO)
            contentType?.startsWith("video/") == true -> RemoteMediaValidator.limitBytes(RemoteMediaKind.VIDEO)
            else -> RemoteMediaValidator.limitBytes(RemoteMediaKind.VIDEO)
        }
    }
}
