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
    private const val MIXED_SHARE_MIME = "*/*"
    private const val MAX_MIXED_SHARE_ITEMS = 8
    private fun isMediaMime(type: String?): Boolean =
        ALLOWED_MIME_PREFIXES.any { type?.substringBefore(';')?.trim()?.lowercase()?.startsWith(it) == true }

    fun parse(
        action: String?,
        mimeType: String?,
        uriStrings: List<String?>,
        resolveUriMime: (String) -> String? = { null },
    ): Result<SharedMediaRequest> = runCatching {
        require(action == ACTION_SEND || action == ACTION_SEND_MULTIPLE) {
            "SHARED_MEDIA_ACTION_UNSUPPORTED"
        }

        val normalizedMime = mimeType?.trim().orEmpty().lowercase()
        val mixedShare = action == ACTION_SEND_MULTIPLE && normalizedMime == MIXED_SHARE_MIME
        require(mixedShare || isMediaMime(normalizedMime)) {
            "SHARED_MEDIA_MIME_UNSUPPORTED"
        }
        if (mixedShare) {
            require(uriStrings.isNotEmpty() && uriStrings.size <= MAX_MIXED_SHARE_ITEMS) {
                "SHARED_MEDIA_MIXED_COUNT_INVALID"
            }
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

        // A mixed Android Files share has a generic intent type. Validate every individual
        // content URI before importing any clip so that an unsupported item fails closed.
        if (mixedShare) {
            require(acceptedUris.size == uriStrings.size && acceptedUris.distinct().size == acceptedUris.size) {
                "SHARED_MEDIA_MIXED_STREAM_INVALID"
            }
            require(acceptedUris.all { isMediaMime(resolveUriMime(it)) }) {
                "SHARED_MEDIA_MIXED_TYPE_UNSUPPORTED"
            }
        }

        SharedMediaRequest(
            mimeType = normalizedMime,
            uriStrings = acceptedUris,
        )
    }
}
