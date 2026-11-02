package com.localcut.editor.mobile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class BridgeRemoteApprovalRequest(
    val requestId: String,
)

object BridgeRemoteApprovalRequestParser {
    fun parse(raw: String?): Result<BridgeRemoteApprovalRequest> = runCatching {
        require(!raw.isNullOrBlank()) { "REMOTE_APPROVAL_URI_INVALID" }
        val uri = URI(raw)
        require(uri.scheme == "localcut") { "REMOTE_APPROVAL_URI_INVALID" }
        require(uri.host == "remote") { "REMOTE_APPROVAL_URI_INVALID" }
        require(uri.path == "/approve") { "REMOTE_APPROVAL_URI_INVALID" }
        require(uri.userInfo == null && uri.fragment == null) { "REMOTE_APPROVAL_URI_INVALID" }
        val params = strictRemoteQuery(uri.rawQuery)
        require(params.keys == setOf("requestId")) { "REMOTE_APPROVAL_URI_INVALID" }
        val requestId = params.getValue("requestId")
        require(REMOTE_REQUEST_ID.matches(requestId)) { "REMOTE_APPROVAL_REQUEST_INVALID" }
        BridgeRemoteApprovalRequest(requestId)
    }
}

private val REMOTE_REQUEST_ID = Regex("^[A-Za-z0-9_-]{16,256}$")

private fun strictRemoteQuery(rawQuery: String?): Map<String, String> {
    require(!rawQuery.isNullOrBlank()) { "REMOTE_APPROVAL_QUERY_INVALID" }
    val result = linkedMapOf<String, String>()
    rawQuery.split('&').forEach { pair ->
        require(pair.isNotBlank()) { "REMOTE_APPROVAL_QUERY_INVALID" }
        val separator = pair.indexOf('=')
        require(separator > 0 && separator < pair.lastIndex) { "REMOTE_APPROVAL_QUERY_INVALID" }
        val key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8.name())
        val value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8.name())
        require(key.isNotBlank() && value.isNotBlank()) { "REMOTE_APPROVAL_QUERY_INVALID" }
        require(result.put(key, value) == null) { "REMOTE_APPROVAL_QUERY_INVALID" }
    }
    return result
}
