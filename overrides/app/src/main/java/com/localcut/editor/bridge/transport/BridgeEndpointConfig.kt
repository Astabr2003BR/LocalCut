package com.localcut.editor.bridge.transport

import java.net.URI

data class BridgeEndpoint(
    val httpsBaseUrl: String,
    val wssUrl: String,
)

object BridgeEndpointConfig {
    // Compatibility shim while runtime endpoint persistence is wired into the APK.
    // Removed once LocalCutBridgeControl and BridgeServiceRuntime read BridgeSettingsStore.relayBaseUrl.
    const val HTTPS_BASE_URL: String = ""
    const val WSS_URL: String = ""

    fun isConfigured(): Boolean =
        HTTPS_BASE_URL.startsWith("https://") && WSS_URL.startsWith("wss://")

    fun resolve(rawBaseUrl: String): Result<BridgeEndpoint> = runCatching {
        val raw = rawBaseUrl.trim()
        require(raw.isNotEmpty()) { "bridge relay URL must not be blank" }

        val uri = URI(raw)
        require(uri.scheme.equals("https", ignoreCase = true)) { "bridge relay URL must use https://" }
        require(uri.userInfo == null) { "bridge relay URL must not include user info" }
        require(uri.rawQuery == null) { "bridge relay URL must not include query parameters" }
        require(uri.rawFragment == null) { "bridge relay URL must not include a fragment" }
        require(!uri.host.isNullOrBlank()) { "bridge relay URL must include a valid host" }
        require(uri.port == -1 || uri.port in 1..65_535) { "bridge relay URL contains an invalid port" }

        val path = uri.rawPath.orEmpty()
        require(path.isEmpty() || path == "/") { "bridge relay URL must point to the server root" }

        val authority = requireNotNull(uri.rawAuthority).lowercase()
        BridgeEndpoint(
            httpsBaseUrl = "https://$authority",
            wssUrl = "wss://$authority/v1/device/connect",
        )
    }
}
