package com.localcut.editor.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.EffectParameter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3CreativeSuiteBackendSmokeTest {
    @Test
    fun coreStaticEffectsConstructOnAndroidRuntime() {
        val effects = listOf(
            EffectInstance("brightness", "localcut.color.brightness", parameters = mapOf("amount" to EffectParameter.Number(0.2))),
            EffectInstance("contrast", "localcut.color.contrast", parameters = mapOf("amount" to EffectParameter.Number(1.2))),
            EffectInstance("exposure", "localcut.color.exposure", parameters = mapOf("stops" to EffectParameter.Number(1.0))),
            EffectInstance("saturation", "localcut.color.saturation", parameters = mapOf("amount" to EffectParameter.Number(1.5))),
            EffectInstance("temperature", "localcut.color.temperature", parameters = mapOf("amount" to EffectParameter.Number(0.5))),
            EffectInstance("tint", "localcut.color.tint", parameters = mapOf("amount" to EffectParameter.Number(-0.5))),
            EffectInstance("blur", "localcut.blur.gaussian", parameters = mapOf("radius" to EffectParameter.Number(10.0))),
        )

        val created = Media3EffectStackFactory.create(effects)

        assertEquals(7, created.size)
    }
}
