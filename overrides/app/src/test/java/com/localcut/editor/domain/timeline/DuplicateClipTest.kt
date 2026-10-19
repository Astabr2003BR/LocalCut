package com.localcut.editor.domain.timeline

import com.localcut.editor.domain.history.DuplicateClipCommand
import com.localcut.editor.domain.history.HistoryManager
import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DuplicateClipTest {
    private fun project(): Project {
        val asset = Asset(
            id = "asset-1",
            type = AssetType.VIDEO,
            uri = "content://video/1",
            sourceDurationUs = 10_000_000,
            sizeBytes = 1234,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "fp-1",
        )
        val base = Project.create("p", 1080, 1920, 30).copy(assets = listOf(asset))
        val visual = base.tracks.first { it.type == TrackType.VISUAL }
        val original = Clip(
            id = "clip-a",
            assetId = asset.id,
            startUs = 0,
            durationUs = 2_000_000,
            sourceInUs = 1_000_000,
            sourceOutUs = 3_000_000,
            transform = TransformValue(x = 0.2, y = -0.1, scale = 1.25, rotationDeg = 10.0, opacity = 0.8),
            opacity = 0.75,
            volume = 1.4,
            keyframes = listOf(
                TransformKeyframe(0, TransformValue(scale = 1.0)),
                TransformKeyframe(2_000_000, TransformValue(scale = 1.2)),
            ),
        )
        val next = Clip(
            id = "clip-b",
            assetId = asset.id,
            startUs = 3_000_000,
            durationUs = 1_000_000,
            sourceInUs = 4_000_000,
            sourceOutUs = 5_000_000,
        )
        val later = Clip(
            id = "clip-c",
            assetId = asset.id,
            startUs = 6_000_000,
            durationUs = 1_000_000,
            sourceInUs = 5_000_000,
            sourceOutUs = 6_000_000,
        )
        return base.copy(
            tracks = base.tracks.map {
                if (it.id == visual.id) it.copy(clips = listOf(original, next, later)) else it
            },
        )
    }

    @Test
    fun duplicateCopiesEditablePropertiesAndRipplesLaterClips() {
        val source = project()

        val result = TimelineOps.duplicateClip(source, "clip-a", "clip-a-copy")
        val visual = result.tracks.first { it.type == TrackType.VISUAL }
        val original = source.findClip("clip-a")!!
        val copy = result.findClip("clip-a-copy")!!

        assertEquals(listOf("clip-a", "clip-a-copy", "clip-b", "clip-c"), visual.clips.map { it.id })
        assertEquals(2_000_000L, copy.startUs)
        assertEquals(original.assetId, copy.assetId)
        assertEquals(original.durationUs, copy.durationUs)
        assertEquals(original.sourceInUs, copy.sourceInUs)
        assertEquals(original.sourceOutUs, copy.sourceOutUs)
        assertEquals(original.transform, copy.transform)
        assertEquals(original.opacity, copy.opacity, 0.0)
        assertEquals(original.volume, copy.volume, 0.0)
        assertEquals(original.keyframes, copy.keyframes)

        assertEquals(5_000_000L, result.findClip("clip-b")!!.startUs)
        assertEquals(8_000_000L, result.findClip("clip-c")!!.startUs)
        assertEquals(source.assets, result.assets)
    }

    @Test
    fun duplicateDoesNotMutateSourceProject() {
        val source = project()
        val originalStarts = source.tracks.first { it.type == TrackType.VISUAL }.clips.map { it.startUs }

        TimelineOps.duplicateClip(source, "clip-a", "clip-a-copy")

        assertEquals(listOf("clip-a", "clip-b", "clip-c"), source.tracks.first { it.type == TrackType.VISUAL }.clips.map { it.id })
        assertEquals(originalStarts, source.tracks.first { it.type == TrackType.VISUAL }.clips.map { it.startUs })
    }

    @Test
    fun duplicateRejectsExistingClipId() {
        val error = assertThrows(TimelineEditException::class.java) {
            TimelineOps.duplicateClip(project(), "clip-a", "clip-b")
        }

        assertEquals(TimelineErrorCode.DUPLICATE_CLIP_ID, error.code)
    }

    @Test
    fun duplicateCommandUndoRedoRestoresExactTrack() {
        val source = project()
        val history = HistoryManager(source)

        history.execute(DuplicateClipCommand("clip-a", "clip-a-copy"))
        assertEquals(listOf("clip-a", "clip-a-copy", "clip-b", "clip-c"), history.project.tracks.first { it.type == TrackType.VISUAL }.clips.map { it.id })

        history.undo()
        assertEquals(source.tracks, history.project.tracks)

        history.redo()
        assertEquals(listOf("clip-a", "clip-a-copy", "clip-b", "clip-c"), history.project.tracks.first { it.type == TrackType.VISUAL }.clips.map { it.id })
        assertEquals(5_000_000L, history.project.findClip("clip-b")!!.startUs)
        assertEquals(8_000_000L, history.project.findClip("clip-c")!!.startUs)
    }
}
