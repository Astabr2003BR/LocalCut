package com.localcut.editor.mobile

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.localcut.editor.domain.animation.AudioDrivenTalkPlanner

/** Read-only, local-source duration verification used before confirmed TALK.
 * Errors mean "unknown"; the previous project and clipping remain unchanged.
 */
internal class AndroidAudioSourceDurationProbe(private val context: Context) {
    fun readDurationUs(sourceUri: String): Long? = runCatching {
        val uri = Uri.parse(sourceUri)
        require(uri.scheme == "content" || uri.scheme == "file") {
            "A fonte de áudio precisa ser local"
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            durationMs
                ?.takeIf { it > 0L && it <= AudioDrivenTalkPlanner.MAX_DURATION_US / 1000L }
                ?.let { Math.multiplyExact(it, 1000L) }
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}
