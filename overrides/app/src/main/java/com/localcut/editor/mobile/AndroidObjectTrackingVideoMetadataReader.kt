package com.localcut.editor.mobile

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.ui.editor.ObjectTrackingRawVideoMetadata
import com.localcut.editor.ui.editor.ObjectTrackingVideoMetadataReader

class AndroidObjectTrackingVideoMetadataReader(
    context: Context,
) : ObjectTrackingVideoMetadataReader {
    private val appContext = context.applicationContext

    override fun read(asset: Asset): Result<ObjectTrackingRawVideoMetadata> = runCatching {
        val uri = Uri.parse(asset.uri)
        val retriever = MediaMetadataRetriever()
        try {
            when (uri.scheme?.lowercase()) {
                null, "" -> retriever.setDataSource(asset.uri)
                "file" -> retriever.setDataSource(requireNotNull(uri.path) { "OBJECT_TRACKING_MEDIA_URI_INVALID" })
                else -> retriever.setDataSource(appContext, uri)
            }
            val width = requiredPositiveInt(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                "OBJECT_TRACKING_SOURCE_WIDTH_UNAVAILABLE",
            )
            val height = requiredPositiveInt(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                "OBJECT_TRACKING_SOURCE_HEIGHT_UNAVAILABLE",
            )
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            ObjectTrackingRawVideoMetadata(
                encodedWidthPx = width,
                encodedHeightPx = height,
                rotationDeg = rotation,
            )
        } finally {
            retriever.release()
        }
    }

    private fun requiredPositiveInt(raw: String?, code: String): Int {
        val value = raw?.toIntOrNull()
        require(value != null && value > 0) { code }
        return value
    }
}
