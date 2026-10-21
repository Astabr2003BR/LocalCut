package com.localcut.editor.media

import com.localcut.editor.domain.model.Asset
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePreviewPolicyTest {
    @Test
    fun simpleProjectOnHealthyDeviceUsesHighPreviewQuality() {
        val tier = AdaptivePreviewPolicy.choose(
            project = projectWithVisualClips(count = 2, clipDurationUs = 5_000_000L),
            runtime = PreviewRuntimeConditions(
                memoryClassMb = 512,
                lowRamDevice = false,
                powerSaveMode = false,
                thermalPressure = ThermalPressure.NONE,
            ),
        )

        assertEquals(PreviewQualityTier.HIGH, tier)
        assertEquals(720, tier.maxLongEdge)
    }

    @Test
    fun lowRamOrPowerSaverUsesLowPreviewQuality() {
        val project = projectWithVisualClips(count = 2, clipDurationUs = 5_000_000L)

        assertEquals(
            PreviewQualityTier.LOW,
            AdaptivePreviewPolicy.choose(
                project,
                PreviewRuntimeConditions(128, lowRamDevice = true, powerSaveMode = false),
            ),
        )
        assertEquals(
            PreviewQualityTier.LOW,
            AdaptivePreviewPolicy.choose(
                project,
                PreviewRuntimeConditions(512, lowRamDevice = false, powerSaveMode = true),
            ),
        )
    }

    @Test
    fun complexProjectStepsDownToBalancedQuality() {
        val tier = AdaptivePreviewPolicy.choose(
            project = projectWithVisualClips(count = 48, clipDurationUs = 2_000_000L),
            runtime = PreviewRuntimeConditions(
                memoryClassMb = 512,
                lowRamDevice = false,
                powerSaveMode = false,
            ),
        )

        assertEquals(PreviewQualityTier.BALANCED, tier)
        assertEquals(540, tier.maxLongEdge)
    }

    @Test
    fun severeThermalPressureUsesLowPreviewQuality() {
        val tier = AdaptivePreviewPolicy.choose(
            project = projectWithVisualClips(count = 2, clipDurationUs = 5_000_000L),
            runtime = PreviewRuntimeConditions(
                memoryClassMb = 512,
                lowRamDevice = false,
                powerSaveMode = false,
                thermalPressure = ThermalPressure.SEVERE,
            ),
        )

        assertEquals(PreviewQualityTier.LOW, tier)
    }

    private fun projectWithVisualClips(count: Int, clipDurationUs: Long): Project {
        val asset = Asset(
            id = "image-1",
            type = AssetType.IMAGE,
            uri = "content://image/1",
            sourceDurationUs = null,
            sizeBytes = 1L,
            displayName = "image.jpg",
            mimeType = "image/jpeg",
            fingerprint = "image-1",
        )
        val clips = List(count) { index ->
            Clip(
                id = "clip-$index",
                assetId = asset.id,
                startUs = index * clipDurationUs,
                durationUs = clipDurationUs,
                sourceInUs = 0L,
                sourceOutUs = clipDurationUs,
            )
        }
        val base = Project.create("Preview", 1080, 1920, 30)
        return base.copy(
            assets = listOf(asset),
            tracks = base.tracks.map { track ->
                if (track.id == "visual-1") track.copy(clips = clips) else track
            },
        )
    }
}
