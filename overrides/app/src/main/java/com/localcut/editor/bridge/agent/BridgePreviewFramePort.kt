package com.localcut.editor.bridge.agent

data class BridgePreviewFrame(
    val mimeType: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val positionUs: Long,
) {
    init {
        require(mimeType == "image/jpeg" || mimeType == "image/png") { "unsupported preview mime type" }
        require(bytes.isNotEmpty()) { "preview frame bytes must not be empty" }
        require(width > 0 && height > 0) { "preview frame dimensions must be positive" }
        require(positionUs >= 0L) { "preview frame position must be non-negative" }
    }
}

fun interface BridgePreviewFramePort {
    fun capture(): Result<BridgePreviewFrame>
}

object UnavailableBridgePreviewFramePort : BridgePreviewFramePort {
    override fun capture(): Result<BridgePreviewFrame> =
        Result.failure(IllegalStateException("preview frame port not attached"))
}


internal object BridgePreviewFreshness {
    const val MAX_AGE_MS = 2_000L
    const val MAX_DRIFT_US = 250_000L
    const val MAX_BYTES = 1_048_576
    const val MAX_EDGE = 720

    fun isCurrent(frameGeneration: Long, generation: Long, capturedAtMs: Long, nowMs: Long,
        framePositionUs: Long, positionUs: Long): Boolean {
        if (frameGeneration != generation || capturedAtMs < 0L || nowMs < capturedAtMs ||
            nowMs - capturedAtMs > MAX_AGE_MS || framePositionUs < 0L || positionUs < 0L) return false
        val drift = maxOf(framePositionUs, positionUs) - minOf(framePositionUs, positionUs)
        return drift <= MAX_DRIFT_US
    }

    fun bounded(frame: BridgePreviewFrame): Boolean =
        frame.bytes.size in 1..MAX_BYTES && frame.width in 1..MAX_EDGE && frame.height in 1..MAX_EDGE
}
