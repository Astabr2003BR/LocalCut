package com.localcut.editor.media

import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.CompressorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3AudioProcessingFactoryTest {
    @Test
    fun neutralSettingsDoNotAddProcessor() {
        assertTrue(Media3AudioProcessingFactory.create(AudioProcessingSettings()).isEmpty())
    }

    @Test
    fun nonNeutralSettingsCreateOneLocalProcessor() {
        val processors = Media3AudioProcessingFactory.create(
            AudioProcessingSettings(compressor = CompressorSettings()),
        )

        assertEquals(1, processors.size)
        assertTrue(processors.single() is LocalCutAudioProcessor)
    }
}
