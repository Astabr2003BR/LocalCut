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
