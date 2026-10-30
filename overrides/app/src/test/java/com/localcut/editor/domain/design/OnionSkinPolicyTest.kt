package com.localcut.editor.domain.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnionSkinPolicyTest {
    @Test
    fun frameWindowSnapsPlayheadToExactFrame() {
        val window = OnionSkinPolicy.frameWindow(playheadUs = 419_999L, fps = 25)

        assertEquals(400_000L, window.startUs)
        assertEquals(40_000L, window.durationUs)
    }

    @Test
    fun selectsOnlyAdjacentFramesAndExcludesCurrentFrame() {
        val frameUs = 40_000L
        val ghosts = OnionSkinPolicy.select(
            candidates = listOf(
                OnionSkinCandidate("prev2", 8 * frameUs),
                OnionSkinCandidate("prev1", 9 * frameUs),
                OnionSkinCandidate("current", 10 * frameUs),
                OnionSkinCandidate("next1", 11 * frameUs),
                OnionSkinCandidate("next2", 12 * frameUs),
            ),
            playheadUs = 10 * frameUs,
            fps = 25,
            previousFrames = 1,
            nextFrames = 1,
        )

        assertEquals(listOf("prev1", "next1"), ghosts.map { it.elementId })
        assertEquals(listOf(-1, 1), ghosts.map { it.relativeFrame })
        assertEquals(OnionSkinPolicy.ALPHA_NEAREST, ghosts.first().alpha, 0.0)
    }

    @Test
    fun frameStepSkipsIntermediateGhostFrames() {
        val frameUs = 40_000L
        val ghosts = OnionSkinPolicy.select(
            candidates = listOf(
                OnionSkinCandidate("prev3", 7 * frameUs),
                OnionSkinCandidate("prev2", 8 * frameUs),
                OnionSkinCandidate("prev1", 9 * frameUs),
                OnionSkinCandidate("next1", 11 * frameUs),
                OnionSkinCandidate("next2", 12 * frameUs),
                OnionSkinCandidate("next3", 13 * frameUs),
            ),
            playheadUs = 10 * frameUs,
            fps = 25,
            previousFrames = 3,
            nextFrames = 3,
            frameStep = 2,
        )

        assertEquals(listOf("prev2", "next2"), ghosts.map { it.elementId })
        assertEquals(listOf(-2, 2), ghosts.map { it.relativeFrame })
    }

    @Test
    fun fadesWithDistanceAndCapsGhostCountForMobile() {
        val frameUs = 40_000L
        val faded = OnionSkinPolicy.select(
            candidates = listOf(
                OnionSkinCandidate("p3", 7 * frameUs),
                OnionSkinCandidate("p2", 8 * frameUs),
                OnionSkinCandidate("p1", 9 * frameUs),
            ),
            playheadUs = 10 * frameUs,
            fps = 25,
            previousFrames = 3,
            nextFrames = 0,
        )
        assertTrue(faded[0].alpha > faded[1].alpha)
        assertTrue(faded[1].alpha > faded[2].alpha)

        val bounded = OnionSkinPolicy.select(
            candidates = (0 until 100).map { index -> OnionSkinCandidate("g$index", 9 * frameUs) },
            playheadUs = 10 * frameUs,
            fps = 25,
            previousFrames = 3,
            nextFrames = 3,
        )
        assertEquals(OnionSkinPolicy.MAX_GHOST_ELEMENTS, bounded.size)
    }

    @Test
    fun invalidFpsOrFrameWindowOrStepFailsClosed() {
        assertTrue(runCatching { OnionSkinPolicy.frameDurationUs(0) }.isFailure)
        assertTrue(
            runCatching {
                OnionSkinPolicy.select(
                    candidates = emptyList(),
                    playheadUs = 0L,
                    fps = 25,
                    previousFrames = 4,
                    nextFrames = 1,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                OnionSkinPolicy.select(
                    candidates = emptyList(),
                    playheadUs = 0L,
                    fps = 25,
                    previousFrames = 1,
                    nextFrames = 1,
                    frameStep = 0,
                )
            }.isFailure,
        )
    }
}
