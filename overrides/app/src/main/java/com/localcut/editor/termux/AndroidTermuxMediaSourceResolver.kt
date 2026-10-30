package com.localcut.editor.termux

import android.content.ContentResolver
import android.net.Uri
import com.localcut.editor.domain.model.Asset

class AndroidTermuxMediaSourceResolver(
    private val contentResolver: ContentResolver,
) : TermuxMediaSourceResolver {
    override fun resolve(asset: Asset): TermuxEngineMediaSource {
        val sizeBytes = asset.sizeBytes?.takeIf { it > 0L }
            ?: error("TERMUX_MEDIA_SIZE_UNAVAILABLE")
        val uri = Uri.parse(asset.uri)
        return TermuxEngineMediaSource(sizeBytes) {
            runCatching { contentResolver.openInputStream(uri) }
                .getOrNull()
                ?: error("TERMUX_MEDIA_SOURCE_UNAVAILABLE")
        }
    }
}
