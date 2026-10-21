package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.ClipTransition
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransitionKind
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionEnvelopeSafetyTest {
    @Test
    fun middleClipRejectsOverlappingFadeInAndFadeOutWindows() {
        val asset = Asset(
            id = "video",
            type = AssetType.VIDEO,
            uri = "content://video",
            sourceDurationUs = 3_000_000L,
            sizeBytes = 1L,
            displayName = "video.mp4",
            mimeType = "video/mp4",
            fingerprint = "video",
        )
        val project = Project.create("Overlap", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual-1",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(
                        Clip("a", asset.id, 0L, 1_000_000L, 0L, 1_000_000L),
                        Clip("b", asset.id, 1_000_000L, 1_000_000L, 1_000_000L, 2_000_000L),
                        Clip("c", asset.id, 2_000_000L, 1_000_000L, 2_000_000L, 3_000_000L),
                    ),
                ),
                Track("audio-1", TrackType.AUDIO, 1),
            ),
            transitions = listOf(
                ClipTransition("ab", "a", "b", 700_000L, TransitionKind.FADE_TO_BLACK),
                ClipTransition("bc", "b", "c", 700_000L, TransitionKind.FADE_TO_BLACK),
            ),
        )

        val result = TransitionRenderSpecCompiler.compile(project)

        assertTrue("overlapping fade windows must fail before Media3 render", result.isFailure)
    }
}
