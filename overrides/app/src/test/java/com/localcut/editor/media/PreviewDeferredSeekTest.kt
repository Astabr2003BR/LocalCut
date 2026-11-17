package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewDeferredSeekTest {
    private class RecordingEngine : PreviewEngine {
        var seekCalls = 0
        var lastSeekUs = -1L
        var loadCalls = 0

        override fun load(project: Project): Result<PreviewLoadResult> {
            loadCalls++
            return Result.success(PreviewLoadResult(2_570_000L, 30))
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekToUs(positionUs: Long) {
            seekCalls++
            lastSeekUs = positionUs
        }
        override fun currentPositionUs(): Long = lastSeekUs.coerceAtLeast(0L)
        override fun setErrorCallback(callback: (String) -> Unit) = Unit
        override fun release() = Unit
    }

    private class NoopTicker : PreviewTicker {
        override fun start(onTick: () -> Unit) = Unit
        override fun stop() = Unit
    }

    @Test
    fun localHandoffSeekDoesNotTouchReleasedPlayerAndRestoresAfterReload() {
        val engine = RecordingEngine()
        val controller = DefaultPreviewController(engine, NoopTicker())
        val project = Project.create("deferred seek", 1080, 1920, 30)
        assertTrue(controller.load(project).isSuccess)
        controller.suspendForBackground()

        val targetUs = controller.seekToUsWhileSuspended(1_500_000L)
        assertEquals(1_500_000L, targetUs)
        assertEquals(0, engine.seekCalls)
        assertFalse(controller.state.value.playing)
        assertEquals(1_500_000L, controller.state.value.positionUs)

        assertTrue(controller.load(project).isSuccess)
        controller.seekToUs(targetUs)
        assertEquals(1, engine.seekCalls)
        assertEquals(1_500_000L, engine.lastSeekUs)
        assertEquals(1_500_000L, controller.state.value.positionUs)
    }

    @Test
    fun suspendedSeekClampsBeyondDurationWithoutCallingEngine() {
        val engine = RecordingEngine()
        val controller = DefaultPreviewController(engine, NoopTicker())
        controller.load(Project.create("clamp", 1080, 1920, 30)).getOrThrow()
        controller.suspendForBackground()

        assertEquals(2_570_000L, controller.seekToUsWhileSuspended(9_000_000L))
        assertEquals(2_570_000L, controller.state.value.positionUs)
        assertEquals(0, engine.seekCalls)
    }
}
