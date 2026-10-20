package com.localcut.editor.media

import com.localcut.editor.domain.model.TrackCollisionPolicy
import com.localcut.editor.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3MultitrackBlueprintTest {
    @Test
    fun layeredRenderPlanCompilesToOrderedMedia3VisualSequences() {
        val base = RenderSequencePlan(
            type = TrackType.VISUAL,
            segments = listOf(RenderSegment.Gap(4_000_000L)),
            sourceTrackId = "base",
            sourceTrackOrder = 0,
            sourceCollisionPolicy = TrackCollisionPolicy.EXCLUSIVE,
            laneIndex = 0,
        )
        val overlay = RenderSequencePlan(
            type = TrackType.VISUAL,
            segments = listOf(RenderSegment.Gap(4_000_000L)),
            sourceTrackId = "overlay",
            sourceTrackOrder = 1,
            sourceCollisionPolicy = TrackCollisionPolicy.OVERLAY,
            laneIndex = 0,
        )
        val audio = RenderSequencePlan(
            type = TrackType.AUDIO,
            segments = listOf(RenderSegment.Gap(4_000_000L)),
            sourceTrackId = "audio",
            sourceTrackOrder = 2,
        )
        val plan = RenderPlan(
            width = 1080,
            height = 1920,
            transformSpaceWidth = 1080,
            transformSpaceHeight = 1920,
            fps = 30,
            durationUs = 4_000_000L,
            visualLayers = listOf(base, overlay),
            audioLayers = listOf(audio),
        )

        val blueprint = Media3CompositionBlueprintCompiler.compile(plan)

        assertEquals(2, blueprint.visualLayers.size)
        assertEquals(1, blueprint.audioLayers.size)
        assertEquals(blueprint.visualLayers.first(), blueprint.visual)
        assertEquals(blueprint.audioLayers.first(), blueprint.audio)
        assertEquals(listOf(TrackType.VISUAL, TrackType.VISUAL), blueprint.visualLayers.map { it.type })
    }
}
