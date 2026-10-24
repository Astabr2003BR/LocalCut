package com.localcut.editor.mobile

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object RemoteMediaUrlPolicy {
    private const val MAX_URL_LENGTH = 4_096
    private val IPV4_LITERAL = Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")

    fun parseAndValidate(rawUrl: String): Result<URI> = runCatching {
        val value = rawUrl.trim()
        require(value.isNotEmpty() && value.length <= MAX_URL_LENGTH) { "REMOTE_MEDIA_URL_INVALID" }

        val uri = URI(value)
        require(uri.scheme.equals("https", ignoreCase = true)) { "REMOTE_MEDIA_URL_SCHEME_INVALID" }
        require(!uri.host.isNullOrBlank()) { "REMOTE_MEDIA_URL_HOST_INVALID" }
        require(uri.userInfo == null) { "REMOTE_MEDIA_URL_USERINFO_NOT_ALLOWED" }

        literalAddress(uri.host)?.let { address ->
            requirePublicAddresses(listOf(address)).getOrThrow()
        }

        uri
    }

    fun requirePublicAddresses(addresses: List<InetAddress>): Result<Unit> = runCatching {
        require(addresses.isNotEmpty()) { "REMOTE_MEDIA_ADDRESS_MISSING" }
        require(addresses.none(::isBlocked)) { "REMOTE_MEDIA_ADDRESS_BLOCKED" }
    }

    private fun literalAddress(host: String): InetAddress? {
        val normalized = host.removePrefix("[").removeSuffix("]")
        if (normalized.contains(':')) {
            return runCatching { InetAddress.getByName(normalized) }.getOrNull()
        }
        if (!IPV4_LITERAL.matches(normalized)) return null

        val octets = normalized.split('.').map { part ->
            part.toIntOrNull()?.takeIf { it in 0..255 }
                ?: throw IllegalArgumentException("REMOTE_MEDIA_URL_HOST_INVALID")
        }
        return InetAddress.getByAddress(ByteArray(4) { index -> octets[index].toByte() })
    }

    private fun isBlocked(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }

        if (address is Inet6Address) {
            val first = address.address[0].toInt() and 0xFF
            if ((first and 0xFE) == 0xFC) return true
        }

        if (address is Inet4Address) {
            val bytes = address.address
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 100 && second in 64..127) return true
        }

        return false
    }
}
