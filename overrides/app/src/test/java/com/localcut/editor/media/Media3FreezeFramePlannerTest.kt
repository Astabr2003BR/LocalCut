package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.FreezeSegment
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3FreezeFramePlannerTest {
    @Test
    fun singleFreezeProducesMovingHoldMovingPiecesWithExactPresentationDuration() {
        val item = media(
            presentationDurationUs = 3_500_000L,
            timeMap = TimeMap(
                freezeSegments = listOf(
                    FreezeSegment(
                        startOffsetUs = 1_000_000L,
                        durationUs = 500_000L,
                        sourceOffsetUs = 1_000_000L,
                    ),
                ),
            ),
        )

        val pieces = Media3FreezeFramePlanner.plan(item, fps = 30)

        assertEquals(3, pieces.size)
        assertEquals(3_500_000L, pieces.sumOf { it.presentationDurationUs })

        val before = pieces[0]
        assertFalse(before.isFreezeHold)
        assertEquals(0L, before.sourceInUs)
        assertEquals(1_000_000L, before.sourceOutUs)
        assertEquals(1_000_000L, before.presentationDurationUs)
        assertEquals(0L, before.clipLocalStartUs)
        assertEquals(Media3OutputTracks.AUDIO_VIDEO, before.outputTracks)

        val hold = pieces[1]
        assertTrue(hold.isFreezeHold)
        assertEquals(500_000L, hold.presentationDurationUs)
        assertEquals(1_000_000L, hold.clipLocalStartUs)
        assertEquals(Media3OutputTracks.VIDEO_ONLY, hold.outputTracks)
        assertTrue(hold.sourceInUs >= 1_000_000L)
        assertTrue(hold.sourceOutUs > hold.sourceInUs)
        assertTrue(hold.sourceOutUs <= 3_000_000L)
        assertTrue(hold.timeMap.baseSpeed.isFinite() && hold.timeMap.baseSpeed > 0.0)
        assertTrue(hold.timeMap.freezeSegments.isEmpty())

        val after = pieces[2]
        assertFalse(after.isFreezeHold)
        assertEquals(1_000_000L, after.sourceInUs)
        assertEquals(3_000_000L, after.sourceOutUs)
        assertEquals(2_000_000L, after.presentationDurationUs)
        assertEquals(1_500_000L, after.clipLocalStartUs)
        assertEquals(Media3OutputTracks.AUDIO_VIDEO, after.outputTracks)
    }

    @Test
    fun freezeAtSourceEndUsesTrailingFrameWindowInsteadOfEmptyClip() {
        val item = media(
            presentationDurationUs = 3_500_000L,
            timeMap = TimeMap(
                freezeSegments = listOf(
                    FreezeSegment(
                        startOffsetUs = 3_000_000L,
                        durationUs = 500_000L,
                        sourceOffsetUs = 3_000_000L,
                    ),
                ),
            ),
        )

        val hold = Media3FreezeFramePlanner.plan(item, fps = 30).single { it.isFreezeHold }

        assertTrue(hold.sourceInUs < 3_000_000L)
        assertEquals(3_000_000L, hold.sourceOutUs)
        assertTrue(hold.sourceOutUs > hold.sourceInUs)
    }

    @Test
    fun plannerRejectsReverseRampAndAudioFadeCombinationsInsteadOfHidingThem() {
        val freeze = FreezeSegment(500_000L, 250_000L, 500_000L)

        assertFalse(
            Media3FreezeFramePlanner.canRender(
                assetType = AssetType.VIDEO,
                timeMap = TimeMap(reverse = true, freezeSegments = listOf(freeze)),
                fadeInUs = 0L,
                fadeOutUs = 0L,
            ),
        )
        assertFalse(
            Media3FreezeFramePlanner.canRender(
                assetType = AssetType.VIDEO,
                timeMap = TimeMap(
                    speedPoints = listOf(SpeedPoint(0L, 1.0), SpeedPoint(1_000_000L, 2.0)),
                    freezeSegments = listOf(freeze),
                ),
                fadeInUs = 0L,
                fadeOutUs = 0L,
            ),
        )
        assertFalse(
            Media3FreezeFramePlanner.canRender(
                assetType = AssetType.VIDEO,
                timeMap = TimeMap(freezeSegments = listOf(freeze)),
                fadeInUs = 100_000L,
                fadeOutUs = 0L,
            ),
        )
        assertFalse(
            Media3FreezeFramePlanner.canRender(
                assetType = AssetType.AUDIO,
                timeMap = TimeMap(freezeSegments = listOf(freeze)),
                fadeInUs = 0L,
                fadeOutUs = 0L,
            ),
        )
    }

    @Test
    fun compositionPolicyAcceptsSafeFreezeButStillRejectsReverse() {
        val freezeMap = TimeMap(
            freezeSegments = listOf(FreezeSegment(500_000L, 250_000L, 500_000L)),
        )
        val safe = renderMedia(durationUs = 1_250_000L, timeMap = freezeMap)
        val reversed = renderMedia(durationUs = 1_000_000L, timeMap = TimeMap(reverse = true))

        assertTrue(Media3CompositionSupportPolicy.validate(plan(safe), RenderMode.EXPORT).isSuccess)
        assertTrue(Media3CompositionSupportPolicy.validate(plan(reversed), RenderMode.EXPORT).isFailure)
    }

    private fun media(
        presentationDurationUs: Long,
        timeMap: TimeMap,
    ): Media3BlueprintItem.Media = Media3BlueprintItem.Media(
        clipId = "clip",
        assetId = "asset",
        assetType = AssetType.VIDEO,
        uri = "content://video",
        outputTracks = Media3OutputTracks.AUDIO_VIDEO,
        sourceMediaDurationUs = 3_000_000L,
        presentationDurationUs = presentationDurationUs,
        sourceInUs = 0L,
        sourceOutUs = 3_000_000L,
        transform = TransformValue(),
        opacity = 1.0,
        volume = 1.0,
        keyframes = emptyList(),
        imageFrameRate = null,
        timeMap = timeMap,
    )

    private fun renderMedia(
        durationUs: Long,
        timeMap: TimeMap,
    ): RenderSegment.Media = RenderSegment.Media(
        clipId = "clip",
        assetId = "asset",
        assetType = AssetType.VIDEO,
        uri = "content://video",
        sourceMediaDurationUs = 1_000_000L,
        durationUs = durationUs,
        sourceInUs = 0L,
        sourceOutUs = 1_000_000L,
        transform = TransformValue(),
        opacity = 1.0,
        volume = 1.0,
        keyframes = emptyList(),
        imageFrameRate = null,
        timeMap = timeMap,
    )

    private fun plan(segment: RenderSegment.Media): RenderPlan = RenderPlan(
        width = 1080,
        height = 1920,
        transformSpaceWidth = 1080,
        transformSpaceHeight = 1920,
        fps = 30,
        durationUs = segment.durationUs,
        visualLayers = listOf(RenderSequencePlan(TrackType.VISUAL, listOf(segment))),
        audioLayers = emptyList(),
    )
}
