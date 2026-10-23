package com.localcut.editor.media

import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.SpeedPoint
import com.localcut.editor.domain.model.TimeMap
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.model.TransformValue
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3CreativeSuiteSupportPolicyTest {
    @Test
    fun supportedStaticEffectAndConstantSpeedPassPolicy() {
        val segment = media(
            effects = listOf(
                EffectInstance(
                    id = "contrast",
                    typeId = "localcut.color.contrast",
                    parameters = mapOf("amount" to EffectParameter.Number(1.2)),
                ),
            ),
            timeMap = TimeMap(baseSpeed = 2.0),
        )

        assertTrue(Media3CompositionSupportPolicy.validate(plan(segment), RenderMode.EXPORT).isSuccess)
    }

    @Test
    fun speedRampPassesPolicyWhenRampBackendIsEnabled() {
        val segment = media(
            timeMap = TimeMap(speedPoints = listOf(SpeedPoint(0L, 1.0), SpeedPoint(500_000L, 2.0))),
        )

        assertTrue(Media3CompositionSupportPolicy.validate(plan(segment), RenderMode.EXPORT).isSuccess)
    }

    @Test
    fun glowShaderEffectPassesPolicyWhenBackendIsEnabled() {
        val segment = media(effects = listOf(EffectInstance("glow", "localcut.glow")))

        assertTrue(Media3CompositionSupportPolicy.validate(plan(segment), RenderMode.EXPORT).isSuccess)
    }

    private fun media(
        effects: List<EffectInstance> = emptyList(),
        timeMap: TimeMap = TimeMap(),
    ): RenderSegment.Media = RenderSegment.Media(
        clipId = "clip",
        assetId = "asset",
        assetType = AssetType.VIDEO,
        uri = "content://video",
        sourceMediaDurationUs = 1_000_000L,
        durationUs = if (timeMap.baseSpeed == 2.0 && timeMap.speedPoints.isEmpty()) 500_000L else 1_000_000L,
        sourceInUs = 0L,
        sourceOutUs = 1_000_000L,
        transform = TransformValue(),
        opacity = 1.0,
        volume = 1.0,
        keyframes = emptyList(),
        imageFrameRate = null,
        effects = effects,
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
