package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositionGraph
import com.localcut.editor.domain.model.CompositionNode
import com.localcut.editor.domain.model.CompositionNodeKind
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RippleDeleteUiPolicyTest {
    @Test
    fun enabledOnlyForUnlockedExclusiveTrackAndUnlockedLayer() {
        val project = project()
        assertTrue(RippleDeleteUiPolicy.isAvailable(project, "clip"))

        assertFalse(
            RippleDeleteUiPolicy.isAvailable(
                project.copy(tracks = listOf(project.tracks.single().copy(locked = true))),
                "clip",
            ),
        )
        assertFalse(
            RippleDeleteUiPolicy.isAvailable(
                project.copy(
                    tracks = listOf(
                        project.tracks.single().copy(collisionPolicy = TrackCollisionPolicy.OVERLAY),
                    ),
                ),
                "clip",
            ),
        )
        assertFalse(
            RippleDeleteUiPolicy.isAvailable(
                project.copy(
                    compositionGraph = CompositionGraph(
                        nodes = listOf(
                            CompositionNode(
                                id = "layer-clip",
                                kind = CompositionNodeKind.LAYER,
                                clipId = "clip",
                                locked = true,
                            ),
                        ),
                    ),
                ),
                "clip",
            ),
        )
        assertFalse(RippleDeleteUiPolicy.isAvailable(project, "missing"))
    }

    private fun project(): Project {
        val asset = Asset(
            id = "asset",
            type = AssetType.VIDEO,
            uri = "content://asset",
            sourceDurationUs = 2_000_000L,
            sizeBytes = 1L,
            displayName = "asset.mp4",
            mimeType = "video/mp4",
            fingerprint = "asset",
        )
        val clip = Clip("clip", asset.id, 0L, 1_000_000L, 0L, 1_000_000L)
        return Project.create("Ripple UI", 1080, 1920, 30).copy(
            assets = listOf(asset),
            tracks = listOf(
                Track(
                    id = "visual",
                    type = TrackType.VISUAL,
                    order = 0,
                    collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                    clips = listOf(clip),
                ),
            ),
        )
    }
}
