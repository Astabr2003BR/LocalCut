package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.BeatMarker
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatMarkerCommandsTest {
    @Test
    fun addSortsMarkersAndInverseRemovesOnlyAddedMarker() {
        val project = Project.create("Beat", 1080, 1920, 30).copy(
            beatMarkers = listOf(BeatMarker("beat-a", 2_000_000L, 0.7)),
        )
        val command = AddBeatMarkerCommand(BeatMarker("beat-b", 1_000_000L, 1.0))

        val added = command.apply(project)
        assertEquals(listOf("beat-b", "beat-a"), added.project.beatMarkers.map { it.id })

        val undone = added.inverse.apply(added.project)
        assertEquals(project.beatMarkers, undone.project.beatMarkers)
    }

    @Test
    fun deleteInverseRestoresMarkerAtOriginalIndex() {
        val project = Project.create("Beat", 1080, 1920, 30).copy(
            beatMarkers = listOf(
                BeatMarker("beat-a", 1_000_000L, 0.5),
                BeatMarker("beat-b", 2_000_000L, 0.8),
                BeatMarker("beat-c", 3_000_000L, 1.0),
            ),
        )

        val deleted = DeleteBeatMarkerCommand("beat-b").apply(project)
        assertEquals(listOf("beat-a", "beat-c"), deleted.project.beatMarkers.map { it.id })

        val restored = deleted.inverse.apply(deleted.project)
        assertEquals(project.beatMarkers, restored.project.beatMarkers)
    }

    @Test
    fun addRejectsDuplicateIdAndInvalidStrength() {
        val project = Project.create("Beat", 1080, 1920, 30).copy(
            beatMarkers = listOf(BeatMarker("beat-a", 1_000_000L, 0.5)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AddBeatMarkerCommand(BeatMarker("beat-a", 2_000_000L, 0.8)).apply(project)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AddBeatMarkerCommand(BeatMarker("bad", 2_000_000L, 2.0)).apply(project)
        }
        assertTrue(project.beatMarkers.single().strength == 0.5)
    }
}
