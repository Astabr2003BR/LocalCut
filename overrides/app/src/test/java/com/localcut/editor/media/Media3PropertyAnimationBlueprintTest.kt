package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.PropertyAnimation
import com.localcut.editor.domain.model.PropertyKeyframe
import com.localcut.editor.domain.model.PropertyValue
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3PropertyAnimationBlueprintTest {
    @Test
    fun renderSegmentPropertyAnimationsSurviveBlueprintCompilation() {
        val animation = PropertyAnimation(
            propertyId = "transform.x",
            keyframes = listOf(
                PropertyKeyframe(
                    id = "x0",
                    offsetUs = 0L,
                    value = PropertyValue.Scalar(12.0),
                ),
            ),
        )
        val segment = RenderSegment.Media(
            clipId = "clip-1",
            assetId = "asset-1",
            assetType = AssetType.VIDEO,
            uri = "file:///video.mp4",
            sourceMediaDurationUs = 2_000_000L,
            durationUs = 2_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 2_000_000L,
            transform = TransformValue(),
            opacity = 1.0,
            volume = 1.0,
            keyframes = emptyList(),
            imageFrameRate = null,
            propertyAnimations = listOf(animation),
        )
        val plan = RenderPlan(
            width = 1080,
            height = 1920,
            transformSpaceWidth = 1080,
            transformSpaceHeight = 1920,
            fps = 30,
            durationUs = 2_000_000L,
            visualLayers = listOf(RenderSequencePlan(TrackType.VISUAL, listOf(segment))),
            audioLayers = emptyList(),
        )

        val blueprint = Media3CompositionBlueprintCompiler.compile(plan)
        val media = blueprint.visualLayers.single().items.single() as Media3BlueprintItem.Media

        assertEquals(listOf(animation), media.propertyAnimations)
    }
}
