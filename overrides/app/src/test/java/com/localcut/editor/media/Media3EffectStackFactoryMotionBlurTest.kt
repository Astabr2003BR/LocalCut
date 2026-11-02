package com.localcut.editor.media

import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import com.localcut.editor.domain.model.PropertyAnimation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3EffectStackFactoryMotionBlurTest {
    @Test
    fun validStaticMotionBlurIsRenderableAndCreatesCustomTemporalEffect() {
        val effect = motionBlur()

        assertTrue(Media3EffectStackFactory.canRender(effect))
        val created = Media3EffectStackFactory.create(listOf(effect))
        assertTrue(created.single() is LocalCutMotionBlurEffect)
    }

    @Test
    fun invalidOrAnimatedMotionBlurFailsClosed() {
        val invalid = motionBlur(shutterAngle = 361.0)
        val animated = motionBlur().copy(
            animations = listOf(PropertyAnimation(propertyId = "shutterAngle")),
        )

        assertFalse(Media3EffectStackFactory.canRender(invalid))
        assertFalse(Media3EffectStackFactory.canRender(animated))
    }

    private fun motionBlur(
        shutterAngle: Double = 180.0,
        samples: Double = 8.0,
    ): EffectInstance = EffectInstance(
        id = "motion-blur",
        typeId = "localcut.motion_blur",
        parameters = mapOf(
            "shutterAngle" to EffectParameter.Number(shutterAngle),
            "samples" to EffectParameter.Number(samples),
        ),
    )
}
