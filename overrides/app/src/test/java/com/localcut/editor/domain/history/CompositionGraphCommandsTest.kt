package com.localcut.editor.domain.history

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionGraphCommandsTest {
    @Test
    fun setCompositionGraphIsUndoable() {
        val initial = projectWithClip()
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode(
                    id = "layer-clip-1",
                    kind = CompositionNodeKind.LAYER,
                    clipId = "clip-1",
                    visible = false,
                    locked = true,
                ),
            ),
        )
        val history = HistoryManager(initial)

        history.execute(SetCompositionGraphCommand(graph))
        assertEquals(graph, history.project.compositionGraph)
        assertTrue(history.undo())
        assertEquals(initial.compositionGraph, history.project.compositionGraph)
        assertTrue(history.redo())
        assertEquals(graph, history.project.compositionGraph)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidParentCycleIsRejected() {
        val initial = projectWithClip()
        val graph = CompositionGraph(
            nodes = listOf(
                CompositionNode("group-a", CompositionNodeKind.GROUP, parentId = "group-b"),
                CompositionNode("group-b", CompositionNodeKind.GROUP, parentId = "group-a"),
            ),
        )

        HistoryManager(initial).execute(SetCompositionGraphCommand(graph))
    }

    private fun projectWithClip(): Project {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 1_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp",
        )
        val clip = Clip(
            id = "clip-1",
            assetId = asset.id,
            startUs = 0L,
            durationUs = 1_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 1_000_000L,
        )
        return Project.create("Composition", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track("visual-1", TrackType.VISUAL, 0, clips = listOf(clip)),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
        )
    }
}
