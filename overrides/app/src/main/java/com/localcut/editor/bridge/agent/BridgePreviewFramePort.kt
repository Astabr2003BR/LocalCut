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
