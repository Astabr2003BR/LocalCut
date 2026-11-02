package com.localcut.editor.ui.editor

import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.media.Media3CompositingEffectFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaToolsUiPolicyTest {
    @Test
    fun everyPresetEnablesRenderableChromaKey() {
        ChromaKeyColorPreset.entries.forEach { preset ->
            val result = ChromaToolsUiPolicy.setKeyColor(CompositingSettings(), preset)
            assertTrue(result.chromaKey.enabled)
            assertEquals(preset.argb, result.chromaKey.keyArgb)
            assertEquals(0.0, result.chromaKey.edgeFeatherPx, 0.0)
            assertTrue(Media3CompositingEffectFactory.canRender(result))
        }
    }

    @Test
    fun selectedPresetTracksExactArgb() {
        ChromaKeyColorPreset.entries.forEach { preset ->
            val result = ChromaToolsUiPolicy.setKeyColor(CompositingSettings(), preset)
            assertEquals(preset, ChromaToolsUiPolicy.selectedPreset(result))
        }
    }

    @Test
    fun labelsStayCompactForMobile() {
        ChromaKeyColorPreset.entries.forEach { preset ->
            assertTrue(ChromaToolsUiPolicy.label(preset).length <= 8)
        }
    }
}
