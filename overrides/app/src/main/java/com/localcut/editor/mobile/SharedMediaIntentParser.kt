package com.localcut.editor.mobile

import java.net.URI

data class SharedMediaRequest(
    val mimeType: String,
    val uriStrings: List<String>,
)

object SharedMediaIntentParser {
    private const val ACTION_SEND = "android.intent.action.SEND"
    private const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    private val ALLOWED_MIME_PREFIXES = listOf("image/", "video/", "audio/")

    fun parse(
        action: String?,
        mimeType: String?,
        uriStrings: List<String?>,
    ): Result<SharedMediaRequest> = runCatching {
        require(action == ACTION_SEND || action == ACTION_SEND_MULTIPLE) {
            "SHARED_MEDIA_ACTION_UNSUPPORTED"
        }

        val normalizedMime = mimeType?.trim().orEmpty()
        require(ALLOWED_MIME_PREFIXES.any { normalizedMime.lowercase().startsWith(it) }) {
            "SHARED_MEDIA_MIME_UNSUPPORTED"
        }

        val acceptedUris = uriStrings.mapNotNull { raw ->
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return@mapNotNull null
            val parsed = runCatching { URI(value) }.getOrNull() ?: return@mapNotNull null
            if (!parsed.scheme.equals("content", ignoreCase = true)) return@mapNotNull null
            value
        }

        if (action == ACTION_SEND) {
            require(acceptedUris.size == 1) { "SHARED_MEDIA_STREAM_INVALID" }
        } else {
            require(acceptedUris.isNotEmpty()) { "SHARED_MEDIA_STREAM_MISSING" }
        }

        SharedMediaRequest(
            mimeType = normalizedMime,
            uriStrings = acceptedUris,
        )
    }
}
