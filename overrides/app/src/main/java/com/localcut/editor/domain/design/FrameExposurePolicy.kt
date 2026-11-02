package com.localcut.editor.domain.design

data class FrameExposureWindow(
    val startUs: Long,
    val durationUs: Long,
)

object FrameExposurePolicy {
    const val MAX_EXPOSURE_FRAMES = 24

    fun window(playheadUs: Long, fps: Int, frameCount: Int): FrameExposureWindow {
        require(playheadUs >= 0L) { "playhead must be non-negative" }
        require(fps in 1..240) { "fps must be in 1..240" }
        require(frameCount in 1..MAX_EXPOSURE_FRAMES) { "frameCount must be in 1..$MAX_EXPOSURE_FRAMES" }
        val frame = frameIndex(playheadUs, fps)
        val startUs = timeForFrame(frame, fps)
        val endUs = timeForFrame(Math.addExact(frame, frameCount.toLong()), fps)
        return FrameExposureWindow(startUs = startUs, durationUs = endUs - startUs)
    }

    private fun frameIndex(timeUs: Long, fps: Int): Long {
        val seconds = timeUs / 1_000_000L
        val remainderUs = timeUs % 1_000_000L
        return Math.addExact(
            Math.multiplyExact(seconds, fps.toLong()),
            remainderUs * fps.toLong() / 1_000_000L,
        )
    }

    private fun timeForFrame(frame: Long, fps: Int): Long {
        require(frame >= 0L) { "frame must be non-negative" }
        val seconds = frame / fps.toLong()
        val remainderFrames = frame % fps.toLong()
        return Math.addExact(
            Math.multiplyExact(seconds, 1_000_000L),
            remainderFrames * 1_000_000L / fps.toLong(),
        )
    }
}
