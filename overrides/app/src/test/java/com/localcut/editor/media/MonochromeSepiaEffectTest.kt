package com.localcut.editor.media

import androidx.media3.effect.RgbMatrix
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonochromeSepiaEffectTest {
    @Test
    fun monochromeAtZeroIsIdentityAndFullAmountUsesLumaMatrix() {
        val identity = matrixFor("localcut.color.monochrome", 0.0)
        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
            identity,
            0.0001f,
        )

        val full = matrixFor("localcut.color.monochrome", 1.0)
        assertArrayEquals(
            floatArrayOf(
                0.2126f, 0.7152f, 0.0722f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f,
                0.2126f, 0.7152f, 0.0722f, 0f,
                0f, 0f, 0f, 1f,
            ),
            full,
            0.0001f,
        )
    }

    @Test
    fun sepiaAtZeroIsIdentityAndFullAmountUsesSepiaMatrix() {
        val identity = matrixFor("localcut.color.sepia", 0.0)
        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
            identity,
            0.0001f,
        )

        val full = matrixFor("localcut.color.sepia", 1.0)
        assertArrayEquals(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f,
                0.349f, 0.686f, 0.168f, 0f,
                0.272f, 0.534f, 0.131f, 0f,
                0f, 0f, 0f, 1f,
            ),
            full,
            0.0001f,
        )
    }

    private fun matrixFor(typeId: String, amount: Double): FloatArray {
        val effect = EffectInstance(
            id = "test-$typeId",
            typeId = typeId,
            parameters = mapOf("amount" to EffectParameter.Number(amount)),
        )
        assertTrue(Media3EffectStackFactory.canRender(effect))
        val rendered = Media3EffectStackFactory.create(listOf(effect)).single()
        assertTrue(rendered is RgbMatrix)
        return (rendered as RgbMatrix).getMatrix(0L, false)
    }
}
