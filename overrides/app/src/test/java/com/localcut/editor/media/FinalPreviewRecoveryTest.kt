package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalPreviewRecoveryTest {
    @Test
    fun restoreReloadsProjectAndReturnsToPreviousPosition() {
        val controller = RecordingPreviewController()
        val project = Project.createEmpty("test").copy(width = 1080, height = 1920, fps = 30)

        val result = FinalPreviewRecovery.restore(
            controller = controller,
            project = project,
            positionUs = 4_500_000L,
            durationUs = 10_000_000L,
        )

        assertTrue(result.isSuccess)
        assertEquals(project, controller.loadedProject)
        assertEquals(4_500_000L, controller.lastSeekUs)
    }

    @Test
    fun restoreClampsPositionToDuration() {
        val controller = RecordingPreviewController()
        val project = Project.createEmpty("test")

        FinalPreviewRecovery.restore(
            controller = controller,
            project = project,
            positionUs = 50_000_000L,
            durationUs = 8_000_000L,
        )

        assertEquals(8_000_000L, controller.lastSeekUs)
    }

    private class RecordingPreviewController : PreviewController {
        private val mutableState = MutableStateFlow(PreviewState())
        override val state: StateFlow<PreviewState> = mutableState
        var loadedProject: Project? = null
        var lastSeekUs: Long? = null

        override fun load(project: Project): Result<Unit> {
            loadedProject = project
            return Result.success(Unit)
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekToUs(positionUs: Long) {
            lastSeekUs = positionUs
        }
        override fun stepFrames(deltaFrames: Int, fps: Int) = Unit
        override fun setLoopRange(startUs: Long, endUs: Long?) = Unit
        override fun release() = Unit
    }
}
