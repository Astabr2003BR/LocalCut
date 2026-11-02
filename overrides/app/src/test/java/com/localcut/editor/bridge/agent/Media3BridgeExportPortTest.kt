package com.localcut.editor.bridge.agent

import com.localcut.editor.domain.model.Project
import com.localcut.editor.media.ExportController
import com.localcut.editor.media.ExportDestination
import com.localcut.editor.media.ExportEvent
import com.localcut.editor.media.ExportProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3BridgeExportPortTest {
    @Test
    fun mapsMedia3EventsToBridgeListenerAndReturnsSafeDestinationMetadata() {
        val controller = FakeController(
            events = listOf(
                ExportEvent.Started,
                ExportEvent.Progress(42),
                ExportEvent.Completed,
            ),
        )
        val listener = RecordingListener()
        val port = Media3BridgeExportPort(
            exportController = controller,
            scope = CoroutineScope(Dispatchers.Unconfined),
            targetFactory = BridgeExportTargetFactory {
                Result.success(
                    BridgeExportTarget(
                        destination = NoopDestination,
                        resultJson = "{\"destination\":\"LOCALCUT_MEDIASTORE\",\"displayName\":\"LocalCut-1.mp4\"}",
                    ),
                )
            },
        )

        val result = port.start(Project.create("Test", 1080, 1920, 30), listener)

        assertTrue(result.isSuccess)
        assertEquals(listOf("started", "progress:42", "completed:LocalCut-1.mp4"), listener.events)
        assertEquals(1, controller.exportCalls)
    }

    @Test
    fun mapsFailureAndCancellationAndCancelDelegatesToController() {
        val failedListener = RecordingListener()
        val failedPort = portFor(listOf(ExportEvent.Started, ExportEvent.Failed("encoder failed")))
        assertTrue(failedPort.start(Project.create("Test", 1080, 1920, 30), failedListener).isSuccess)
        assertEquals(listOf("started", "failed:encoder failed"), failedListener.events)

        val cancelledController = FakeController(listOf(ExportEvent.Started, ExportEvent.Cancelled))
        val cancelledListener = RecordingListener()
        val cancelledPort = Media3BridgeExportPort(
            cancelledController,
            CoroutineScope(Dispatchers.Unconfined),
            BridgeExportTargetFactory { Result.success(BridgeExportTarget(NoopDestination, "{\"destination\":\"LOCALCUT_MEDIASTORE\"}")) },
        )
        assertTrue(cancelledPort.start(Project.create("Test", 1080, 1920, 30), cancelledListener).isSuccess)
        assertEquals(listOf("started", "cancelled"), cancelledListener.events)
        assertTrue(cancelledPort.cancel().isSuccess)
        assertEquals(1, cancelledController.cancelCalls)
    }

    @Test
    fun targetCreationFailureDoesNotStartMedia3() {
        val controller = FakeController(emptyList())
        val port = Media3BridgeExportPort(
            controller,
            CoroutineScope(Dispatchers.Unconfined),
            BridgeExportTargetFactory { Result.failure(IllegalStateException("media store unavailable")) },
        )

        val result = port.start(Project.create("Test", 1080, 1920, 30), RecordingListener())

        assertTrue(result.isFailure)
        assertEquals(0, controller.exportCalls)
    }

    private fun portFor(events: List<ExportEvent>): Media3BridgeExportPort = Media3BridgeExportPort(
        exportController = FakeController(events),
        scope = CoroutineScope(Dispatchers.Unconfined),
        targetFactory = BridgeExportTargetFactory { Result.success(BridgeExportTarget(NoopDestination, "{\"destination\":\"LOCALCUT_MEDIASTORE\"}")) },
    )

    private class FakeController(private val events: List<ExportEvent>) : ExportController {
        var exportCalls = 0
        var cancelCalls = 0

        override fun export(project: Project, destination: ExportDestination): Flow<ExportEvent> = flow {
            exportCalls += 1
            events.forEach { emit(it) }
        }

        override fun export(project: Project, destination: ExportDestination, profile: ExportProfile): Flow<ExportEvent> =
            export(project, destination)

        override fun cancel() {
            cancelCalls += 1
        }
    }

    private object NoopDestination : ExportDestination {
        override fun commitFrom(tempFile: File): Result<Unit> = Result.success(Unit)
    }

    private class RecordingListener : BridgeExportListener {
        val events = mutableListOf<String>()
        override fun onStarted() { events += "started" }
        override fun onProgress(percent: Int) { events += "progress:$percent" }
        override fun onCompleted(resultJson: String) {
            val name = Regex("\\\"displayName\\\":\\\"([^\\\"]+)\\\"").find(resultJson)?.groupValues?.get(1)
            events += if (name == null) "completed" else "completed:$name"
        }
        override fun onFailed(message: String) { events += "failed:$message" }
        override fun onCancelled() { events += "cancelled" }
    }
}
