package com.localcut.editor.recipes

import com.localcut.editor.domain.model.Clip
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeRecipeApplicatorTest {
    private val clip = Clip(
        id = "clip",
        assetId = "asset",
        startUs = 0L,
        durationUs = 2_000_000L,
        sourceInUs = 0L,
        sourceOutUs = 2_000_000L,
        effects = listOf(EffectInstance("grain", "localcut.grain")),
    )

    @Test
    fun impactZoomExpandsIntoOrdinaryScaleAnimationAndPreservesEffects() {
        val result = AnimeRecipeApplicator.apply(clip, AnimeRecipe.IMPACT_ZOOM)

        val scale = result.propertyAnimations.single { it.propertyId == "transform.scale" }
        assertEquals(PropertyValue.Scalar(1.0), scale.keyframes.first().value)
        assertTrue((scale.keyframes[1].value as PropertyValue.Scalar).value > 1.0)
        assertTrue(result.effects.any { it.typeId == "localcut.grain" })
    }

    @Test
    fun cameraShakeIsDeterministicForSameSeed() {
        val a = AnimeRecipeApplicator.apply(clip, AnimeRecipe.CAMERA_SHAKE, seed = 42L)
        val b = AnimeRecipeApplicator.apply(clip, AnimeRecipe.CAMERA_SHAKE, seed = 42L)

        assertEquals(a.propertyAnimations, b.propertyAnimations)
        assertTrue(a.propertyAnimations.any { it.propertyId == "transform.x" })
        assertTrue(a.propertyAnimations.any { it.propertyId == "transform.y" })
    }

    @Test
    fun parallaxPanStaysInsideClipDuration() {
        val result = AnimeRecipeApplicator.apply(clip, AnimeRecipe.PARALLAX_PAN)

        assertTrue(result.propertyAnimations.flatMap { it.keyframes }.all { it.offsetUs in 0L..clip.durationUs })
        assertTrue(result.validationIssues().isEmpty())
    }
}
