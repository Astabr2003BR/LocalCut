package com.localcut.editor.mobile

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMediaUrlPolicyTest {
    @Test
    fun acceptsPublicHttpsUrl() {
        val uri = RemoteMediaUrlPolicy.parseAndValidate(
            "https://cdn.example/media/video.mp4",
        ).getOrThrow()

        assertEquals("https", uri.scheme)
        assertEquals("cdn.example", uri.host)
    }

    @Test
    fun acceptsSignedHttpsMediaLink() {
        val value = "https://cdn.example/private/test.jpg?expires=123&signature=valid-token"
        val parsed = RemoteMediaUrlPolicy.parseAndValidate(value).getOrThrow()
        assertEquals(value, parsed.toASCIIString())
    }

    @Test
    fun rejectsChatGptInternalAndUnsafeMediaLinks() {
        val invalid = listOf(
            "sandbox:/mnt/data/LocalCut_Personagem_LipSync.jpg",
            "data:image/png;base64,AA==",
            "file:///sdcard/Download/voice.mp3",
            "http://cdn.example/media/voice.mp3",
            "https://user:password@cdn.example/image.png",
            "https://127.0.0.1:443/audio.mp3",
            "https://cdn.example/" + "a".repeat(4_100),
        )
        invalid.forEach { url ->
            assertTrue("expected rejected media link: " + url.take(70),
                RemoteMediaUrlPolicy.parseAndValidate(url).isFailure)
        }
    }

    @Test
    fun doesNotRequireNetworkOrDnsForPublicHostPrevalidation() {
        val result = RemoteMediaUrlPolicy.parseAndValidate("https://media.example/assets/voice.mp3")
        assertTrue(result.isSuccess)
    }

    @Test
    fun rejectsHttpUrl() {
        assertTrue(
            RemoteMediaUrlPolicy.parseAndValidate(
                "http://cdn.example/media/video.mp4",
            ).isFailure,
        )
    }

    @Test
    fun rejectsLoopbackLiteralUrl() {
        assertTrue(
            RemoteMediaUrlPolicy.parseAndValidate(
                "https://127.0.0.1/video.mp4",
            ).isFailure,
        )
    }

    @Test
    fun rejectsPrivateAndIpv6UlaAddresses() {
        val blocked = listOf(
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.10",
            "169.254.1.1",
            "100.64.0.1",
            "::1",
            "fc00::1",
            "fe80::1",
            "ff02::1",
            "0.0.0.0",
        ).map(InetAddress::getByName)

        blocked.forEach { address ->
            assertTrue(
                "expected blocked: ${address.hostAddress}",
                RemoteMediaUrlPolicy.requirePublicAddresses(listOf(address)).isFailure,
            )
        }
    }

    @Test
    fun acceptsPublicAddress() {
        val result = RemoteMediaUrlPolicy.requirePublicAddresses(
            listOf(InetAddress.getByName("1.1.1.1")),
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun rejectsMixedPublicAndPrivateDnsAnswer() {
        val result = RemoteMediaUrlPolicy.requirePublicAddresses(
            listOf(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("192.168.1.20"),
            ),
        )

        assertTrue(result.isFailure)
    }
}
