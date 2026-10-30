package com.localcut.editor.data.project

import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Test

class BeatMarkerPersistenceTest {
    @Test
    fun schemaV6RoundTripPreservesBeatMarkers() {
        val project = Project.create("Beat", 1080, 1920, 30).copy(
            beatMarkers = listOf(BeatMarker("manual-beat-0", 0L, 0.8)),
        )

        val decoded = ProjectJson.decode(ProjectJson.encode(project))

        assertEquals(project.beatMarkers, decoded.beatMarkers)
        assertEquals(0L, decoded.beatMarkers.single().timeUs)
        assertEquals(0.8, decoded.beatMarkers.single().strength, 0.0)
    }
}
