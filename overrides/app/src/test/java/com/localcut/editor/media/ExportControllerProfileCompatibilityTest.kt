package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportControllerProfileCompatibilityTest {
    @Test
    fun profileOverloadDefaultsToLegacyExportImplementation() = runBlocking {
        val controller = LegacyExportController()
        val project = Project.create("Compatibility", 1080, 1920, 30)
        val destination = ExportDestination { Result.success(Unit) }
        val profile = ExportProfile.defaultFor(project)

        val events = controller.export(project, destination, profile).toList()

        assertEquals(listOf(ExportEvent.Started, ExportEvent.Completed), events)
        assertEquals(1, controller.legacyCalls)
    }

    private class LegacyExportController : ExportController {
        var legacyCalls = 0

        override fun export(project: Project, destination: ExportDestination): Flow<ExportEvent> {
            legacyCalls++
            return flowOf(ExportEvent.Started, ExportEvent.Completed)
        }

        override fun cancel() = Unit
    }
}
