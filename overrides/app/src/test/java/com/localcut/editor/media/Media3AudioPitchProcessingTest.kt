package com.localcut.editor.media

import androidx.media3.common.audio.SonicAudioProcessor
import com.localcut.editor.audio.AudioPitchPolicy
import com.localcut.editor.domain.model.AudioProcessingSettings
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3AudioPitchProcessingTest {
    @Test
    fun semitoneConversionUsesEqualTemperedPitchRatio() {
        assertEquals(1.0f, AudioPitchPolicy.factorForSemitones(0.0), 0.000001f)
        assertEquals(2.0f, AudioPitchPolicy.factorForSemitones(12.0), 0.000001f)
        assertEquals(0.5f, AudioPitchPolicy.factorForSemitones(-12.0), 0.000001f)
        assertTrue(abs(AudioPitchPolicy.factorForSemitones(7.0) - 1.498307f) < 0.00001f)
    }

    @Test
    fun pitchOnlyUsesSonicWithoutAddingNoOpLocalDsp() {
        val processors = Media3AudioProcessingFactory.create(
            AudioProcessingSettings(pitchSemitones = 7.0),
        )

        assertEquals(1, processors.size)
        assertTrue(processors.single() is SonicAudioProcessor)
    }

    @Test
    fun pitchAndDspAreChainedWithoutChangingTimeMap() {
        val processors = Media3AudioProcessingFactory.create(
            AudioProcessingSettings(
                pitchSemitones = -5.0,
                normalizationGainDb = 3.0,
            ),
        )

        assertEquals(2, processors.size)
        assertTrue(processors[0] is SonicAudioProcessor)
        assertTrue(processors[1] is LocalCutAudioProcessor)
    }

    @Test
    fun pitchValidationRejectsNonFiniteAndOutOfRangeValues() {
        assertTrue(AudioProcessingSettings(pitchSemitones = Double.NaN).validationIssues().isNotEmpty())
        assertTrue(AudioProcessingSettings(pitchSemitones = 24.01).validationIssues().isNotEmpty())
        assertTrue(AudioProcessingSettings(pitchSemitones = -24.01).validationIssues().isNotEmpty())
        assertTrue(AudioProcessingSettings(pitchSemitones = 24.0).validationIssues().isEmpty())
        assertTrue(AudioProcessingSettings(pitchSemitones = -24.0).validationIssues().isEmpty())
    }
}
