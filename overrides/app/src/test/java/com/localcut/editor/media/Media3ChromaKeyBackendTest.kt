package com.localcut.editor.media

import com.localcut.editor.domain.model.BlendMode
import com.localcut.editor.domain.model.ChromaKeySettings
import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskPoint
import com.localcut.editor.domain.model.MaskSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ChromaKeyBackendTest {
    @Test
    fun staticChromaKeyIsSupportedWithoutUnsupportedBlend() {
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
    fun rectangleAndEllipseMasksAreSupportedByStaticShaderBackend() {
        listOf(MaskKind.RECTANGLE, MaskKind.ELLIPSE).forEach { kind ->
            val settings = CompositingSettings(
                masks = listOf(
                    MaskSpec(
                        id = "m",
                        kind = kind,
                        points = listOf(MaskPoint(0.2, 0.2), MaskPoint(0.8, 0.8)),
                        featherPx = 12.0,
                        expansionPx = 4.0,
                    ),
                ),
            )
            assertTrue("expected $kind to be renderable", Media3CompositingEffectFactory.canRender(settings))
        }
    }

    @Test
    fun freeformAndBlendRemainBlockedUntilTheirDedicatedBackendsExist() {
        val freeform = CompositingSettings(
            masks = listOf(
                MaskSpec(
                    id = "free",
                    kind = MaskKind.FREEFORM,
                    points = listOf(MaskPoint(0.1, 0.1), MaskPoint(0.9, 0.9)),
                ),
            ),
        )
        val blend = CompositingSettings(blendMode = BlendMode.SCREEN)

        assertFalse(Media3CompositingEffectFactory.canRender(freeform))
        assertFalse(Media3CompositingEffectFactory.canRender(blend))
    }
}
