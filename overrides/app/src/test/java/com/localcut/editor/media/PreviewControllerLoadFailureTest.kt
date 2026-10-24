package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewControllerLoadFailureTest {
    @Test
    fun synchronousPreviewLoadFailureIsPublishedInsteadOfMasqueradingAsEmptyProject() {
        val controller = DefaultPreviewController(
            engine = FailingPreviewEngine("composition rejected"),
            ticker = NoopPreviewTicker(),
        )

        val result = controller.load(Project.create("preview-error", 1080, 1920, 30))

        assertTrue(result.isFailure)
        assertEquals("composition rejected", controller.state.value.errorMessage)
        assertEquals(0L, controller.state.value.durationUs)
    }

    private class FailingPreviewEngine(private val message: String) : PreviewEngine {
        override fun load(project: Project): Result<PreviewLoadResult> =
            Result.failure(IllegalStateException(message))

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekToUs(positionUs: Long) = Unit
        override fun currentPositionUs(): Long = 0L
        override fun setErrorCallback(callback: (String) -> Unit) = Unit
        override fun release() = Unit
    }

    private class NoopPreviewTicker : PreviewTicker {
        override fun start(onTick: () -> Unit) = Unit
        override fun stop() = Unit
    }
}
