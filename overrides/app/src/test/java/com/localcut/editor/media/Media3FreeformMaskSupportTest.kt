package com.localcut.editor.media

import com.localcut.editor.domain.model.CompositingSettings
import com.localcut.editor.domain.model.MaskKind
import com.localcut.editor.domain.model.MaskPoint
import com.localcut.editor.domain.model.MaskSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3FreeformMaskSupportTest {
    @Test
    fun freeformPolygonUpToEightPointsIsAccepted() {
        val settings = CompositingSettings(
            masks = listOf(
                MaskSpec(
                    id = "freeform",
                    kind = MaskKind.FREEFORM,
                    points = listOf(
                        MaskPoint(0.5, 0.1),
                        MaskPoint(0.9, 0.5),
                        MaskPoint(0.5, 0.9),
                        MaskPoint(0.1, 0.5),
                    ),
                    featherPx = 8.0,
                    expansionPx = 2.0,
                ),
            ),
        )

        assertTrue(Media3CompositingEffectFactory.canRender(settings))
    }

    @Test
    fun freeformPolygonOutsideBoundedBackendIsRejected() {
        val tooFew = CompositingSettings(
            masks = listOf(
                MaskSpec(
                    id = "freeform",
                    kind = MaskKind.FREEFORM,
                    points = listOf(MaskPoint(0.1, 0.1), MaskPoint(0.9, 0.9)),
                ),
            ),
        )
        val tooMany = CompositingSettings(
            masks = listOf(
                MaskSpec(
                    id = "freeform",
                    kind = MaskKind.FREEFORM,
                    points = (0..8).map { i -> MaskPoint(i / 8.0, 0.5) },
                ),
            ),
        )

        assertFalse(Media3CompositingEffectFactory.canRender(tooFew))
        assertFalse(Media3CompositingEffectFactory.canRender(tooMany))
    }
}
