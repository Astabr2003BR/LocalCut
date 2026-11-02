package com.localcut.editor.ui.editor

import kotlin.math.roundToLong

object CameraCutFrameSnapPolicy {
    fun snap(timeUs: Long, fps: Int): Long {
        require(timeUs >= 0L) { "camera cut time must be non-negative" }
        require(fps in 1..240) { "camera cut fps must be in 1..240" }

        val wholeSeconds = timeUs / MICROS_PER_SECOND
        val remainderUs = timeUs % MICROS_PER_SECOND
        val baseFrames = Math.multiplyExact(wholeSeconds, fps.toLong())
        val fractionalFrames = (remainderUs.toDouble() * fps.toDouble() / MICROS_PER_SECOND.toDouble()).roundToLong()
        val frameIndex = Math.addExact(baseFrames, fractionalFrames)

        val snappedWholeSeconds = frameIndex / fps.toLong()
        val frameRemainder = frameIndex % fps.toLong()
        val wholeUs = Math.multiplyExact(snappedWholeSeconds, MICROS_PER_SECOND)
        val partialUs = (frameRemainder.toDouble() * MICROS_PER_SECOND.toDouble() / fps.toDouble()).roundToLong()
        return Math.addExact(wholeUs, partialUs)
    }

    private const val MICROS_PER_SECOND = 1_000_000L
}
