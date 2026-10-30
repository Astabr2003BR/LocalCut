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
