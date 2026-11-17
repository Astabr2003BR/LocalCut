package com.localcut.editor.bridge.agent

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import java.util.concurrent.atomic.AtomicLong
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

internal object PreviewFrameSurfacePolicy {
    fun canCaptureSurfaceView(width: Int, height: Int, surfaceValid: Boolean): Boolean =
        width > 0 && height > 0 && surfaceValid

    fun canCaptureTextureView(width: Int, height: Int, available: Boolean): Boolean =
        width > 0 && height > 0 && available
}

@UnstableApi
@OptIn(ExperimentalApi::class)
class AndroidPlayerPreviewFramePort(
    private val positionUsProvider: () -> Long,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : BridgePreviewFramePort {
    @Volatile
    private var playerViewRef: WeakReference<PlayerView>? = null
    private data class CaptureTicket(val generation: Long, val view: PlayerView, val positionUs: Long, val startedAtMs: Long)
    private data class CachedFrame(val frame: BridgePreviewFrame, val generation: Long, val capturedAtMs: Long)
    private val generation = AtomicLong(0L)
    private val latestFrame = AtomicReference<CachedFrame?>(null)
    private val latestError = AtomicReference("preview frame not ready")
    private val captureInFlight = AtomicBoolean(false)

    fun attach(view: PlayerView) {
        if (playerViewRef?.get() !== view) {
            invalidate()
            playerViewRef = WeakReference(view)
        }
        requestRefresh()
    }

    fun detach(view: PlayerView) {
        if (playerViewRef?.get() === view) {
            playerViewRef = null
            invalidate()
            latestError.set("preview surface unavailable")
        }
    }

    /** Invalidate before loading, seeking, or changing the bound surface. In-flight callbacks cannot publish old frames. */
    fun invalidate() {
        generation.incrementAndGet()
        latestFrame.set(null)
        latestError.set("preview frame not ready")
    }

    fun requestRefresh() {
        val view = playerViewRef?.get() ?: run {
            latestError.set("preview surface unavailable")
            return
        }
        if (!captureInFlight.compareAndSet(false, true)) return
        val requestedGeneration = generation.get()
        val run = Runnable {
            if (playerViewRef?.get() !== view || requestedGeneration != generation.get()) {
                captureInFlight.set(false)
            } else {
                captureFrom(view, requestedGeneration)
            }
        }
        if (Looper.myLooper() == mainHandler.looper) run.run()
        else if (!mainHandler.post(run)) captureInFlight.set(false)
    }

    override fun capture(): Result<BridgePreviewFrame> {
        requestRefresh()
        val cached = latestFrame.get()
        if (cached != null && BridgePreviewFreshness.isCurrent(
                cached.generation, generation.get(), cached.capturedAtMs, SystemClock.elapsedRealtime(),
                cached.frame.positionUs, positionUsProvider())) {
            return Result.success(cached.frame)
        }
        return Result.failure(IllegalStateException(latestError.get()))
    }

    private fun captureFrom(playerView: PlayerView, requestedGeneration: Long) {
        val player = playerView.player
        if (player == null || player.playbackState != Player.STATE_READY) {
            finishFailure("preview player is not ready")
            return
        }
        val ticket = CaptureTicket(requestedGeneration, playerView, positionUsProvider().coerceAtLeast(0L), SystemClock.elapsedRealtime())
        val surface = playerView.videoSurfaceView ?: run {
            finishFailure("preview surface is not ready")
            return
        }

        when (surface) {
            is SurfaceView -> {
                val surfaceValid = runCatching { surface.holder.surface.isValid }.getOrDefault(false)
                if (!PreviewFrameSurfacePolicy.canCaptureSurfaceView(
                        width = surface.width,
                        height = surface.height,
                        surfaceValid = surfaceValid,
                    )
                ) {
                    finishFailure("preview surface is not ready")
                    return
                }
            }

            is TextureView -> {
                if (!PreviewFrameSurfacePolicy.canCaptureTextureView(
                        width = surface.width,
                        height = surface.height,
                        available = surface.isAvailable,
                    )
                ) {
                    finishFailure("preview surface is not ready")
                    return
                }
            }

            else -> {
                finishFailure("preview surface type is not capturable")
                return
            }
        }

        val size = runCatching { PreviewFrameCapturePolicy.targetSize(surface.width, surface.height) }
            .getOrElse {
                finishFailure(it.message ?: "invalid preview surface")
                return
            }

        when (surface) {
            is SurfaceView -> captureSurfaceView(surface, size, ticket)
            is TextureView -> captureTextureView(surface, size, ticket)
        }
    }

    private fun captureSurfaceView(surface: SurfaceView, size: PreviewFrameSize, ticket: CaptureTicket) {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val request = runCatching {
            PixelCopy.request(
                surface,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        storeBitmap(bitmap, ticket)
                    } else {
                        bitmap.recycle()
                        finishFailure("preview capture failed: PixelCopy=$result")
                    }
                },
                mainHandler,
            )
        }
        if (request.isFailure) {
            if (!bitmap.isRecycled) bitmap.recycle()
            finishFailure("preview surface is not ready")
        }
    }

    private fun captureTextureView(texture: TextureView, size: PreviewFrameSize, ticket: CaptureTicket) {
        val bitmap = texture.getBitmap(size.width, size.height)
        if (bitmap == null) {
            finishFailure("preview texture is not ready")
            return
        }
        storeBitmap(bitmap, ticket)
    }

    private fun storeBitmap(bitmap: Bitmap, ticket: CaptureTicket) {
        try {
            if (playerViewRef?.get() !== ticket.view || ticket.view.player?.playbackState != Player.STATE_READY ||
                !BridgePreviewFreshness.isCurrent(ticket.generation, generation.get(), ticket.startedAtMs,
                    SystemClock.elapsedRealtime(), ticket.positionUs, positionUsProvider())) {
                latestError.set("preview changed during capture")
                return
            }
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, PreviewFrameCapturePolicy.JPEG_QUALITY, output)) {
                    "preview JPEG compression failed"
                }
                output.toByteArray()
            }
            val frame = BridgePreviewFrame("image/jpeg", bytes, bitmap.width, bitmap.height, ticket.positionUs)
            check(BridgePreviewFreshness.bounded(frame)) { "preview exceeds technical bounds" }
            if (ticket.generation == generation.get() && playerViewRef?.get() === ticket.view) {
                latestFrame.set(CachedFrame(frame, ticket.generation, ticket.startedAtMs))
            }
            latestError.set("preview frame not ready")
        } catch (error: Throwable) {
            latestError.set(error.message ?: "preview frame encoding failed")
        } finally {
            bitmap.recycle()
            captureInFlight.set(false)
        }
    }

    private fun finishFailure(message: String) {
        latestFrame.set(null)
        latestError.set(message)
        captureInFlight.set(false)
    }
}
