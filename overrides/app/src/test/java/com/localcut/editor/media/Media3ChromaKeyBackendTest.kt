package com.localcut.editor.media

import com.localcut.editor.domain.model.ChromaKeySettings
import com.localcut.editor.domain.model.CompositingSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ChromaKeyBackendTest {
    @Test
    fun staticChromaKeyIsSupportedWithoutMasksOrBlend() {
        val settings = CompositingSettings(
            chromaKey = ChromaKeySettings(
                enabled = true,
                keyArgb = 0xFF00FF00.toInt(),
                threshold = 0.25,
                softness = 0.1,
                spillSuppression = 0.4,
                edgeFeatherPx = 0.0,
            ),
        )

        assertTrue(Media3CompositingEffectFactory.canRender(settings))
    }

    @Test
    fun masksRemainBlockedUntilMaskShaderBackendIsEnabled() {
        val settings = CreativeCompositingFixtures.ellipseMask()
        assertFalse(Media3CompositingEffectFactory.canRender(settings))
    }
}

private object CreativeCompositingFixtures {
    fun ellipseMask() = CompositingSettings(
        masks = listOf(
            com.localcut.editor.domain.model.MaskSpec(
                id = "m",
                kind = com.localcut.editor.domain.model.MaskKind.ELLIPSE,
            ),
        ),
    )
}
