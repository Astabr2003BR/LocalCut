package com.localcut.editor.mobile

import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteMediaDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var tempRoot: File
    private lateinit var trustedClient: OkHttpClient
    private val loopback = listOf(InetAddress.getByName("127.0.0.1"))

    @Before
    fun setUp() {
        val serverCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("media.test")
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
        tempRoot = Files.createTempDirectory("localcut-remote-test").toFile()
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
        tempRoot.deleteRecursively()
    }

    @Test
    fun downloadsHttpsBodyToPrivateTemporaryFile() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "image/png")
                .body("abc")
                .build(),
        )
        val downloader = testDownloader()

        val downloaded = downloader.download(mediaUrl("/image.png")).getOrThrow()

        assertTrue(downloaded.file.exists())
        assertEquals("abc", downloaded.file.readText())
        assertEquals("image/png", downloaded.declaredContentType)
        assertEquals(3L, downloaded.byteCount)
    }

    @Test
    fun manuallyFollowsRedirectAndRevalidatesEveryHop() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", mediaUrl("/final.png"))
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "image/png")
                .body("ok")
                .build(),
        )
        var validationCount = 0
        val downloader = testDownloader(
            addressValidator = {
                validationCount += 1
                Result.success(Unit)
            },
        )

        val result = downloader.download(mediaUrl("/start")).getOrThrow()

        assertEquals("ok", result.file.readText())
        assertEquals(2, validationCount)
    }

    @Test
    fun rejectsFourthRedirect() = runBlocking {
        repeat(4) { index ->
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", mediaUrl("/redirect-${index + 1}"))
                    .build(),
            )
        }
        val downloader = testDownloader(maxRedirects = 3)

        val result = downloader.download(mediaUrl("/start"))

        assertTrue(result.isFailure)
        assertTempDirectoryEmpty()
    }

    @Test
    fun rejectsAddressBeforeOpeningSocket() = runBlocking {
        server.enqueue(MockResponse.Builder().body("must-not-be-read").build())
        val downloader = testDownloader(
            addressValidator = { Result.failure(IllegalArgumentException("REMOTE_MEDIA_ADDRESS_BLOCKED")) },
        )

        val result = downloader.download(mediaUrl("/blocked"))

        assertTrue(result.isFailure)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        assertTempDirectoryEmpty()
    }

    @Test
    fun abortsOversizedStreamAndDeletesPartialFile() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "image/png")
                .body("0123456789")
                .build(),
        )
        val downloader = testDownloader(
            provisionalLimitBytes = { 4L },
        )

        val result = downloader.download(mediaUrl("/large.png"))

        assertTrue(result.isFailure)
        assertTempDirectoryEmpty()
    }

    private fun testDownloader(
        addressValidator: (List<InetAddress>) -> Result<Unit> = { Result.success(Unit) },
        provisionalLimitBytes: (String?) -> Long = { 1_024L },
        maxRedirects: Int = 3,
    ): RemoteMediaDownloader = RemoteMediaDownloader(
        cacheDir = tempRoot,
        baseClient = trustedClient,
        addressResolver = RemoteMediaAddressResolver { loopback },
        addressValidator = addressValidator,
        provisionalLimitBytes = provisionalLimitBytes,
        maxRedirects = maxRedirects,
    )

    private fun mediaUrl(path: String): String = "https://media.test:${server.port}$path"

    private fun assertTempDirectoryEmpty() {
        val remoteDir = File(tempRoot, "remote-import")
        assertFalse(remoteDir.exists() && remoteDir.listFiles().orEmpty().isNotEmpty())
    }
}
