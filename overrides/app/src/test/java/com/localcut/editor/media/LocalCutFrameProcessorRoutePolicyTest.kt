package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.ChromaKeySettings
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskSpec
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.Track
import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCutFrameProcessorRoutePolicyTest {
    @Test
    fun plainVisualProjectKeepsDefaultMedia3Processor() {
        assertFalse(LocalCutFrameProcessorRoutePolicy.requiresCustomProcessor(projectWith(CompositingSettings())))
    }

    @Test
    fun nonNormalBlendRequiresCustomProcessor() {
        assertTrue(
            LocalCutFrameProcessorRoutePolicy.requiresCustomProcessor(
                projectWith(CompositingSettings(blendMode = BlendMode.SCREEN)),
            ),
        )
    }

    @Test
    fun maskRequiresCustomProcessor() {
        assertTrue(
            LocalCutFrameProcessorRoutePolicy.requiresCustomProcessor(
                projectWith(
                    CompositingSettings(
                        masks = listOf(MaskSpec(id = "mask", kind = MaskKind.RECTANGLE)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun chromaKeyRequiresCustomProcessor() {
        assertTrue(
            LocalCutFrameProcessorRoutePolicy.requiresCustomProcessor(
                projectWith(
                    CompositingSettings(chromaKey = ChromaKeySettings(enabled = true)),
                ),
            ),
        )
    }


    @Test
    fun singlePortraitAndSeparateAudioNeverRequireMultiInputVideoGraph() {
        val base = projectWith(CompositingSettings())
        val audio = Track(
            id = "audio-1", type = TrackType.AUDIO, order = 1,
            clips = listOf(Clip(
                id = "voice", assetId = "voice-asset", startUs = 0L,
                durationUs = 1_000_000L, sourceInUs = 0L, sourceOutUs = 1_000_000L,
            )),
        )
        assertFalse(LocalCutFrameProcessorRoutePolicy.requiresMultipleInputVideoGraph(base))
        assertFalse(LocalCutFrameProcessorRoutePolicy.requiresMultipleInputVideoGraph(
            base.copy(tracks = base.tracks + audio),
        ))
    }

    @Test
    fun twoVisibleVisualSequencesRequireMultiInputVideoGraph() {
        val base = projectWith(CompositingSettings())
        val secondVisual = base.tracks.single().copy(id = "visual-2", order = 2)
        assertTrue(LocalCutFrameProcessorRoutePolicy.requiresMultipleInputVideoGraph(
            base.copy(tracks = base.tracks + secondVisual),
        ))
    }

    @Test
    fun hiddenOrEmptyVisualTracksDoNotActivateExtraVideoInputs() {
        val base = projectWith(CompositingSettings())
        val hidden = base.tracks.single().copy(id = "visual-hidden", order = 2, hidden = true)
        val empty = base.tracks.single().copy(id = "visual-empty", order = 3, clips = emptyList())
        assertFalse(LocalCutFrameProcessorRoutePolicy.requiresMultipleInputVideoGraph(
            base.copy(tracks = base.tracks + hidden + empty),
        ))
    }

    private fun projectWith(compositing: CompositingSettings): Project = Project.create(
        title = "route",
        width = 1280,
        height = 720,
        fps = 30,
    ).copy(
        tracks = listOf(
            Track(
                id = "visual-1",
                type = TrackType.VISUAL,
                order = 0,
                collisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
                clips = listOf(
                    Clip(
                        id = "clip",
                        assetId = "asset",
                        startUs = 0L,
                        durationUs = 1_000_000L,
                        sourceInUs = 0L,
                        sourceOutUs = 1_000_000L,
                        compositing = compositing,
                    ),
                ),
            ),
        ),
    )
}
