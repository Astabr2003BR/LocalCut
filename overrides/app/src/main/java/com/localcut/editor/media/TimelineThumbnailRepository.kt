package com.localcut.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import java.io.InputStream
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_THUMBNAIL_CACHE_BYTES = 12 * 1024 * 1024

data class TimelineThumbnailRequest(
    val key: String,
    val uri: String,
    val sourceTimeUs: Long,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
)

class TimelineThumbnailRepository(
    context: Context,
    private val executor: Executor,
    cacheBytes: Int = DEFAULT_THUMBNAIL_CACHE_BYTES,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val lock = Any()
    private val cache = object : LruCache<String, Bitmap>(cacheBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }
    private val inFlight = mutableMapOf<String, MutableList<(Result<Bitmap>) -> Unit>>()

    fun request(
        request: TimelineThumbnailRequest,
        callback: (Result<Bitmap>) -> Unit,
    ) {
        var cached: Bitmap? = null
        var shouldDecode = false
        synchronized(lock) {
            cached = cache.get(request.key)
            if (cached == null) {
                val callbacks = inFlight[request.key]
                if (callbacks != null) {
                    callbacks += callback
                } else {
                    inFlight[request.key] = mutableListOf(callback)
                    shouldDecode = true
                }
            }
        }

        cached?.let {
            callback(Result.success(it))
            return
        }
        if (!shouldDecode) return

        executor.execute {
            val result = runCatching { decode(request) }
            val callbacks = synchronized(lock) {
                result.onSuccess { bitmap -> cache.put(request.key, bitmap) }
                inFlight.remove(request.key).orEmpty()
            }
            callbacks.forEach { pendingCallback -> pendingCallback(result) }
        }
    }

    private fun decode(request: TimelineThumbnailRequest): Bitmap {
        val uri = Uri.parse(request.uri)
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
        val path = uri.lastPathSegment.orEmpty().lowercase()
        val looksLikeVideo = mimeType.startsWith("video/") ||
            path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") ||
            path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".3gp")

        val decoded = if (looksLikeVideo) {
            decodeVideo(uri, request.sourceTimeUs)
        } else {
            decodeImage(uri)
        }
        return scaleDown(
            decoded,
            request.targetWidthPx.coerceAtLeast(1),
            request.targetHeightPx.coerceAtLeast(1),
        )
    }

    private fun decodeVideo(uri: Uri, sourceTimeUs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            return checkNotNull(
                retriever.getFrameAtTime(
                    sourceTimeUs.coerceAtLeast(0L),
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ),
            ) { "No video frame available for $uri" }
        } finally {
            retriever.release()
        }
    }

    private fun decodeImage(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }

        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            targetWidth = 288,
            targetHeight = 160,
        )
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return open(uri).use { stream ->
            checkNotNull(BitmapFactory.decodeStream(stream, null, options)) {
                "Unable to decode image thumbnail for $uri"
            }
        }
    }

    private fun open(uri: Uri): InputStream = checkNotNull(resolver.openInputStream(uri)) {
        "Unable to open media URI $uri"
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun scaleDown(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap
        val scale = max(
            targetWidth.toFloat() / bitmap.width.toFloat(),
            targetHeight.toFloat() / bitmap.height.toFloat(),
        ).coerceAtMost(1f)
        if (scale >= 1f) return bitmap

        val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
        val scaledHeight = max(1, (bitmap.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
