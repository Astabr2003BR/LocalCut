package com.localcut.editor.domain.design

data class OnionSkinCandidate(
    val elementId: String,
    val startUs: Long,
)

data class OnionSkinGhost(
    val elementId: String,
    val relativeFrame: Int,
    val alpha: Double,
)

data class DrawingFrameWindow(
    val startUs: Long,
    val durationUs: Long,
)

object OnionSkinPolicy {
    const val MAX_FRAME_WINDOW = 3
    const val MAX_GHOST_ELEMENTS = 64
    const val ALPHA_NEAREST = 0.32
    private const val ALPHA_SECOND = 0.20
    private const val ALPHA_THIRD = 0.12

    fun frameDurationUs(fps: Int): Long {
        require(fps in 1..240) { "fps must be in 1..240" }
        return 1_000_000L / fps
    }

    fun frameWindow(playheadUs: Long, fps: Int): DrawingFrameWindow {
        require(playheadUs >= 0L) { "playhead must be non-negative" }
        require(fps in 1..240) { "fps must be in 1..240" }
        val frame = frameIndex(playheadUs, fps)
        val startUs = timeForFrame(frame, fps)
        val endUs = timeForFrame(Math.addExact(frame, 1L), fps)
        return DrawingFrameWindow(startUs = startUs, durationUs = endUs - startUs)
    }

    fun select(
        candidates: List<OnionSkinCandidate>,
        playheadUs: Long,
        fps: Int,
        previousFrames: Int = 1,
        nextFrames: Int = 1,
        frameStep: Int = 1,
    ): List<OnionSkinGhost> {
        require(playheadUs >= 0L) { "playhead must be non-negative" }
        require(previousFrames in 0..MAX_FRAME_WINDOW) { "previous frame window out of range" }
        require(nextFrames in 0..MAX_FRAME_WINDOW) { "next frame window out of range" }
        require(frameStep in 1..MAX_FRAME_WINDOW) { "frame step out of range" }
        require(fps in 1..240) { "fps must be in 1..240" }

        val currentFrame = frameIndex(playheadUs, fps)
        return candidates.asSequence()
            .filter { it.elementId.isNotBlank() && it.startUs >= 0L }
            .mapNotNull { candidate ->
                val deltaLong = frameIndex(candidate.startUs, fps) - currentFrame
                if (deltaLong < Int.MIN_VALUE.toLong() || deltaLong > Int.MAX_VALUE.toLong()) return@mapNotNull null
                val delta = deltaLong.toInt()
                val distance = kotlin.math.abs(delta)
                val insideWindow = when {
                    delta < 0 -> distance <= previousFrames
                    delta > 0 -> distance <= nextFrames
                    else -> false
                }
                val included = insideWindow && distance % frameStep == 0
                if (!included) null else OnionSkinGhost(
                    elementId = candidate.elementId,
                    relativeFrame = delta,
                    alpha = alphaForDistance(distance),
                )
            }
            .sortedWith(
                compareBy<OnionSkinGhost> { kotlin.math.abs(it.relativeFrame) }
                    .thenBy { it.relativeFrame }
                    .thenBy { it.elementId },
            )
            .take(MAX_GHOST_ELEMENTS)
            .toList()
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

    private fun alphaForDistance(distance: Int): Double = when (distance) {
        1 -> ALPHA_NEAREST
        2 -> ALPHA_SECOND
        else -> ALPHA_THIRD
    }
}
