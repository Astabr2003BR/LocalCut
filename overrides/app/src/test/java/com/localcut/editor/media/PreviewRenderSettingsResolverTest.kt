package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewRenderSettingsResolverTest {
    @Test
    fun healthyDeviceUsesHighTierRenderSettings() {
        val project = Project.create("Preview", 1080, 1920, 30)

        val selection = PreviewRenderSettingsResolver.resolve(
            project,
            PreviewRuntimeConditions(
                memoryClassMb = 512,
                lowRamDevice = false,
                powerSaveMode = false,
            ),
        )

        assertEquals(PreviewQualityTier.HIGH, selection.qualityTier)
        assertEquals(RenderOutputSettings.forPreview(PreviewQualityTier.HIGH), selection.outputSettings)
    }

    @Test
    fun lowRamDeviceUsesLowTierRenderSettings() {
        val project = Project.create("Preview", 1080, 1920, 30)

        val selection = PreviewRenderSettingsResolver.resolve(
            project,
            PreviewRuntimeConditions(
                memoryClassMb = 128,
                lowRamDevice = true,
                powerSaveMode = false,
            ),
        )

        assertEquals(PreviewQualityTier.LOW, selection.qualityTier)
        assertEquals(360, selection.outputSettings.previewMaxLongEdge)
    }
}
