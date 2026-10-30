package com.localcut.editor.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import com.localcut.editor.audio.AudioPitchPolicy
import com.localcut.editor.audio.StatefulAudioDsp
import com.localcut.editor.domain.model.AudioProcessingSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LocalCutAudioProcessor(
    private val settings: AudioProcessingSettings,
) : BaseAudioProcessor() {
    private var dsp: StatefulAudioDsp? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        dsp = StatefulAudioDsp(
            channelCount = inputAudioFormat.channelCount,
            sampleRateHz = inputAudioFormat.sampleRate,
            settings = settings,
        )
    }

    override fun onReset() {
        dsp = null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val samples = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> readPcm16(inputBuffer)
            C.ENCODING_PCM_FLOAT -> readPcmFloat(inputBuffer)
            else -> error("unsupported PCM encoding")
        }
        val processed = checkNotNull(dsp) { "audio processor must be flushed before queuing input" }
            .process(samples)
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> processed.forEach { sample ->
                val value = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE.toFloat())
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(value.toShort())
            }
            C.ENCODING_PCM_FLOAT -> processed.forEach { output.putFloat(it.coerceIn(-1f, 1f)) }
        }
        output.flip()
    }

    private fun readPcm16(input: ByteBuffer): FloatArray {
        input.order(ByteOrder.nativeOrder())
        val samples = FloatArray(input.remaining() / Short.SIZE_BYTES)
        for (index in samples.indices) samples[index] = input.short / 32768f
        return samples
    }

    private fun readPcmFloat(input: ByteBuffer): FloatArray {
        input.order(ByteOrder.nativeOrder())
        val samples = FloatArray(input.remaining() / Float.SIZE_BYTES)
        for (index in samples.indices) samples[index] = input.float
        return samples
    }
}

object Media3AudioProcessingFactory {
    fun create(settings: AudioProcessingSettings): List<AudioProcessor> {
        require(settings.validationIssues().isEmpty()) {
            "invalid audio processing: ${settings.validationIssues().first()}"
        }
        return buildList {
            if (settings.pitchSemitones != 0.0) {
                add(
                    SonicAudioProcessor().apply {
                        setSpeed(1.0f)
                        setPitch(AudioPitchPolicy.factorForSemitones(settings.pitchSemitones))
                    },
                )
            }
            if (settings.hasSampleDsp()) add(LocalCutAudioProcessor(settings))
        }
    }
}
