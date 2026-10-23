package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.Project

@Composable
fun AudioToolsPanel(
    project: Project,
    selectedClipId: String?,
    onSetAudioProcessing: (AudioProcessingSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (clip == null) {
            Text(
                "Selecione um clipe com áudio para editar o processamento.",
                modifier = Modifier.padding(12.dp),
            )
            return@Surface
        }

        val settings = clip.audioProcessing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Áudio profissional", style = MaterialTheme.typography.titleSmall)

            AudioCommitSlider(
                label = "Pan",
                initial = settings.pan,
                range = -1f..1f,
            ) { value ->
                onSetAudioProcessing(AudioToolsUiPolicy.setPan(settings, value))
            }
            AudioCommitSlider(
                label = "Ganho / normalização",
                initial = settings.normalizationGainDb,
                range = -24f..24f,
                suffix = " dB",
            ) { value ->
                onSetAudioProcessing(AudioToolsUiPolicy.setNormalizationGain(settings, value))
            }

            Text("EQ 3 bandas", style = MaterialTheme.typography.labelLarge)
            AudioCommitSlider(
                label = "Graves 120 Hz",
                initial = AudioToolsUiPolicy.eqGain(settings, AudioEqBand.LOW),
                range = -12f..12f,
                suffix = " dB",
            ) { value ->
                onSetAudioProcessing(AudioToolsUiPolicy.setEqGain(settings, AudioEqBand.LOW, value))
            }
            AudioCommitSlider(
                label = "Médios 1 kHz",
                initial = AudioToolsUiPolicy.eqGain(settings, AudioEqBand.MID),
                range = -12f..12f,
                suffix = " dB",
            ) { value ->
                onSetAudioProcessing(AudioToolsUiPolicy.setEqGain(settings, AudioEqBand.MID, value))
            }
            AudioCommitSlider(
                label = "Agudos 8 kHz",
                initial = AudioToolsUiPolicy.eqGain(settings, AudioEqBand.HIGH),
                range = -12f..12f,
                suffix = " dB",
            ) { value ->
                onSetAudioProcessing(AudioToolsUiPolicy.setEqGain(settings, AudioEqBand.HIGH, value))
            }

            AudioToggle(
                label = "Compressor",
                checked = settings.compressor != null,
                onCheckedChange = { enabled ->
                    onSetAudioProcessing(AudioToolsUiPolicy.setCompressorEnabled(settings, enabled))
                },
            )
            AudioToggle(
                label = "Limiter",
                checked = settings.limiter != null,
                onCheckedChange = { enabled ->
                    onSetAudioProcessing(AudioToolsUiPolicy.setLimiterEnabled(settings, enabled))
                },
            )
            AudioToggle(
                label = "Noise gate",
                checked = settings.noiseGate != null,
                onCheckedChange = { enabled ->
                    onSetAudioProcessing(AudioToolsUiPolicy.setNoiseGateEnabled(settings, enabled))
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(
                    onClick = { onSetAudioProcessing(CreativeToolsUiPolicy.voiceAudioPreset(settings)) },
                ) { Text("Voz clara") }
                FilledTonalButton(
                    onClick = { onSetAudioProcessing(CreativeToolsUiPolicy.loudnessAudioPreset(settings)) },
                ) { Text("Loudness") }
                FilledTonalButton(
                    onClick = { onSetAudioProcessing(AudioToolsUiPolicy.reset()) },
                    enabled = settings != AudioProcessingSettings(),
                ) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun AudioCommitSlider(
    label: String,
    initial: Double,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    onCommit: (Double) -> Unit,
) {
    var value by remember(label, initial) {
        mutableFloatStateOf(initial.toFloat().coerceIn(range.start, range.endInclusive))
    }
    Text("$label ${"%.2f".format(value)}$suffix", style = MaterialTheme.typography.labelSmall)
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onCommit(value.toDouble()) },
        valueRange = range,
    )
}

@Composable
private fun AudioToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
