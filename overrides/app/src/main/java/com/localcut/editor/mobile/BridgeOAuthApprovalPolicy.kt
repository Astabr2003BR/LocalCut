package com.localcut.editor.mobile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class BridgeOAuthApprovalRequest(
    val requestId: String,
)

object BridgeOAuthApprovalRequestParser {
    fun parse(raw: String?): Result<BridgeOAuthApprovalRequest> = runCatching {
        require(!raw.isNullOrBlank()) { "OAUTH_APPROVAL_URI_INVALID" }
        val uri = URI(raw)
        require(uri.scheme == "localcut") { "OAUTH_APPROVAL_URI_INVALID" }
        require(uri.host == "oauth") { "OAUTH_APPROVAL_URI_INVALID" }
        require(uri.path == "/approve") { "OAUTH_APPROVAL_URI_INVALID" }
        require(uri.userInfo == null && uri.fragment == null) { "OAUTH_APPROVAL_URI_INVALID" }
        val params = strictQuery(uri.rawQuery)
        require(params.keys == setOf("requestId")) { "OAUTH_APPROVAL_URI_INVALID" }
        val requestId = params.getValue("requestId")
        require(REQUEST_ID.matches(requestId)) { "OAUTH_APPROVAL_REQUEST_INVALID" }
        BridgeOAuthApprovalRequest(requestId)
    }
}

object BridgeOAuthApprovalPolicy {
    fun validateCompletionUrl(
        relayBaseUrl: String,
        requestId: String,
        rawCompletionUrl: String,
    ): Result<String> = runCatching {
        require(REQUEST_ID.matches(requestId)) { "OAUTH_APPROVAL_REQUEST_INVALID" }
        val relay = URI(relayBaseUrl.trim().trimEnd('/'))
        val completion = URI(rawCompletionUrl)

        require(relay.scheme == "https" && completion.scheme == "https") {
            "OAUTH_COMPLETION_URL_INVALID"
        }
        require(relay.userInfo == null && relay.query == null && relay.fragment == null) {
            "OAUTH_COMPLETION_URL_INVALID"
        }
        require(relay.path.isNullOrEmpty() || relay.path == "/") { "OAUTH_COMPLETION_URL_INVALID" }
        require(completion.userInfo == null && completion.fragment == null) {
            "OAUTH_COMPLETION_URL_INVALID"
        }
        require(relay.host.equals(completion.host, ignoreCase = true)) {
            "OAUTH_COMPLETION_URL_INVALID"
        }
        require(effectivePort(relay) == effectivePort(completion)) { "OAUTH_COMPLETION_URL_INVALID" }
        require(completion.path == "/oauth/complete") { "OAUTH_COMPLETION_URL_INVALID" }

        val params = strictQuery(completion.rawQuery)
        require(params.keys == setOf("requestId")) { "OAUTH_COMPLETION_URL_INVALID" }
        require(params.getValue("requestId") == requestId) { "OAUTH_COMPLETION_URL_INVALID" }
        completion.toASCIIString()
    }
}

private val REQUEST_ID = Regex("^[A-Za-z0-9_-]{16,256}$")

private fun strictQuery(rawQuery: String?): Map<String, String> {
    require(!rawQuery.isNullOrBlank()) { "OAUTH_QUERY_INVALID" }
    val result = linkedMapOf<String, String>()
    rawQuery.split('&').forEach { pair ->
        require(pair.isNotBlank()) { "OAUTH_QUERY_INVALID" }
        val separator = pair.indexOf('=')
        require(separator > 0 && separator < pair.lastIndex) { "OAUTH_QUERY_INVALID" }
        val key = decodeQueryComponent(pair.substring(0, separator))
        val value = decodeQueryComponent(pair.substring(separator + 1))
        require(key.isNotBlank() && value.isNotBlank()) { "OAUTH_QUERY_INVALID" }
        require(result.put(key, value) == null) { "OAUTH_QUERY_INVALID" }
    }
    return result
}

private fun decodeQueryComponent(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())

private fun effectivePort(uri: URI): Int = when {
    uri.port >= 0 -> uri.port
    uri.scheme.equals("https", ignoreCase = true) -> 443
    else -> -1
}
