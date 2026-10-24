package com.localcut.editor.bridge.agent

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal data class PreviewFrameSize(
    val width: Int,
    val height: Int,
)

internal object PreviewFrameCapturePolicy {
    const val MAX_EDGE_PX = 720
    const val JPEG_QUALITY = 75

    fun targetSize(sourceWidth: Int, sourceHeight: Int): PreviewFrameSize {
        require(sourceWidth > 0 && sourceHeight > 0) { "preview source dimensions must be positive" }
        val maxSourceEdge = maxOf(sourceWidth, sourceHeight)
        if (maxSourceEdge <= MAX_EDGE_PX) return PreviewFrameSize(sourceWidth, sourceHeight)

        val scale = MAX_EDGE_PX.toDouble() / maxSourceEdge.toDouble()
        return PreviewFrameSize(
            width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
            height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }
}

@UnstableApi
@OptIn(ExperimentalApi::class)
class AndroidPlayerPreviewFramePort(
    private val positionUsProvider: () -> Long,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : BridgePreviewFramePort {
    @Volatile
    private var playerViewRef: WeakReference<PlayerView>? = null
    private val latestFrame = AtomicReference<BridgePreviewFrame?>(null)
    private val latestError = AtomicReference("preview frame not ready")
    private val captureInFlight = AtomicBoolean(false)

    fun attach(view: PlayerView) {
        playerViewRef = WeakReference(view)
        requestRefresh()
    }

    fun detach(view: PlayerView) {
        if (playerViewRef?.get() === view) {
            playerViewRef = null
            latestFrame.set(null)
            latestError.set("preview surface unavailable")
        }
    }

    fun requestRefresh() {
        val view = playerViewRef?.get() ?: run {
            latestError.set("preview surface unavailable")
            return
        }
        if (!captureInFlight.compareAndSet(false, true)) return
        if (Looper.myLooper() == mainHandler.looper) {
            captureFrom(view)
        } else {
            mainHandler.post { captureFrom(view) }
        }
    }

    override fun capture(): Result<BridgePreviewFrame> {
        requestRefresh()
        return latestFrame.get()?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException(latestError.get()))
    }

    private fun captureFrom(playerView: PlayerView) {
        val surface = playerView.videoSurfaceView
        if (surface == null || surface.width <= 0 || surface.height <= 0) {
            finishFailure("preview surface is not ready")
            return
        }
        val size = runCatching { PreviewFrameCapturePolicy.targetSize(surface.width, surface.height) }
            .getOrElse {
                finishFailure(it.message ?: "invalid preview surface")
                return
            }

        when (surface) {
            is SurfaceView -> captureSurfaceView(surface, size)
            is TextureView -> captureTextureView(surface, size)
            else -> finishFailure("preview surface type is not capturable")
        }
    }

    private fun captureSurfaceView(surface: SurfaceView, size: PreviewFrameSize) {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            surface,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    storeBitmap(bitmap)
                } else {
                    bitmap.recycle()
                    finishFailure("preview capture failed: PixelCopy=$result")
                }
            },
            mainHandler,
        )
    }

    private fun captureTextureView(texture: TextureView, size: PreviewFrameSize) {
        val bitmap = texture.getBitmap(size.width, size.height)
        if (bitmap == null) {
            finishFailure("preview texture is not ready")
            return
        }
        storeBitmap(bitmap)
    }

    private fun storeBitmap(bitmap: Bitmap) {
        try {
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, PreviewFrameCapturePolicy.JPEG_QUALITY, output)) {
                    "preview JPEG compression failed"
                }
                output.toByteArray()
            }
            latestFrame.set(
                BridgePreviewFrame(
                    mimeType = "image/jpeg",
                    bytes = bytes,
                    width = bitmap.width,
                    height = bitmap.height,
                    positionUs = positionUsProvider().coerceAtLeast(0L),
                ),
            )
            latestError.set("preview frame not ready")
        } catch (error: Throwable) {
            latestError.set(error.message ?: "preview frame encoding failed")
        } finally {
            bitmap.recycle()
            captureInFlight.set(false)
        }
    }

    private fun finishFailure(message: String) {
        latestError.set(message)
        captureInFlight.set(false)
    }
}
