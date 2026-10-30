package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AudioDuckingSettings
import com.localcut.editor.domain.model.AudioProcessingSettings
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType

@Composable
fun AudioToolsPanel(
    project: Project,
    selectedClipId: String?,
    onSetAudioProcessing: (AudioProcessingSettings) -> Unit,
    onSetTrackDucking: (String, AudioDuckingSettings?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
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
        val selectedTrack = project.tracks.firstOrNull { track -> track.clips.any { it.id == clip.id } }
        val duckingCandidates = project.tracks
            .filter { track -> track.type == TrackType.AUDIO && track.id != selectedTrack?.id }
            .sortedWith(compareBy({ it.order }, { it.id }))
        var section by remember(clip.id) { mutableStateOf(AudioToolsPanelUiPolicy.defaultSection()) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AudioToolsPanelUiPolicy.sections().forEach { candidate ->
                    val label = when (candidate) {
                        AudioToolsPanelSection.BASIC -> "Básico"
                        AudioToolsPanelSection.EQ -> "EQ"
                        AudioToolsPanelSection.DYNAMICS -> "Dinâmica"
                    }
                    if (candidate == section) {
                        FilledTonalButton(onClick = { section = candidate }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { section = candidate }) { Text(label) }
                    }
                }
            }

            when (section) {
                AudioToolsPanelSection.BASIC -> {
                    Text("Presets", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AudioPreset.entries.forEach { preset ->
                            FilledTonalButton(
                                onClick = { onSetAudioProcessing(AudioPresetUiPolicy.apply(settings, preset)) },
                            ) { Text(AudioPresetUiPolicy.label(preset)) }
                        }
                    }

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
                    AudioCommitSlider(
                        label = "Pitch",
                        initial = settings.pitchSemitones,
                        range = -24f..24f,
                        suffix = " st",
                    ) { value ->
                        onSetAudioProcessing(AudioToolsUiPolicy.setPitchSemitones(settings, value))
                    }

                    if (selectedTrack?.type == TrackType.AUDIO) {
                        val ducking = selectedTrack.ducking
                        Text("Auto Ducking", style = MaterialTheme.typography.labelLarge)
                        if (duckingCandidates.isEmpty()) {
                            Text(
                                "Adicione outra faixa de áudio para usar como sidechain.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            AudioToggle(
                                label = "Ativar",
                                checked = ducking != null,
                                onCheckedChange = { enabled ->
                                    onSetTrackDucking(
                                        selectedTrack.id,
                                        if (enabled) {
                                            ducking ?: AudioDuckingSettings(sourceTrackId = duckingCandidates.first().id)
                                        } else {
                                            null
                                        },
                                    )
                                },
                            )
                            ducking?.let { current ->
                                Text("Fonte sidechain", style = MaterialTheme.typography.labelSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    duckingCandidates.forEach { candidate ->
                                        val label = "Áudio ${candidate.order + 1}"
                                        if (candidate.id == current.sourceTrackId) {
                                            FilledTonalButton(
                                                onClick = {
                                                    onSetTrackDucking(selectedTrack.id, current.copy(sourceTrackId = candidate.id))
                                                },
                                            ) { Text(label) }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    onSetTrackDucking(selectedTrack.id, current.copy(sourceTrackId = candidate.id))
                                                },
                                            ) { Text(label) }
                                        }
                                    }
                                }
                                AudioCommitSlider(
                                    label = "Redução",
                                    initial = current.reductionDb,
                                    range = -48f..0f,
                                    suffix = " dB",
                                ) { value ->
                                    onSetTrackDucking(selectedTrack.id, current.copy(reductionDb = value))
                                }
                                AudioCommitSlider(
                                    label = "Attack",
                                    initial = current.attackUs / 1_000.0,
                                    range = 0f..5_000f,
                                    suffix = " ms",
                                ) { value ->
                                    onSetTrackDucking(
                                        selectedTrack.id,
                                        current.copy(attackUs = (value * 1_000.0).toLong().coerceIn(0L, 5_000_000L)),
                                    )
                                }
                                AudioCommitSlider(
                                    label = "Release",
                                    initial = current.releaseUs / 1_000.0,
                                    range = 0f..10_000f,
                                    suffix = " ms",
                                ) { value ->
                                    onSetTrackDucking(
                                        selectedTrack.id,
                                        current.copy(releaseUs = (value * 1_000.0).toLong().coerceIn(0L, 10_000_000L)),
                                    )
                                }
                                Text(
                                    "Mute/solo da faixa fonte suspende o ducking.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onSetAudioProcessing(CreativeToolsUiPolicy.voiceAudioPreset(settings)) },
                        ) { Text("Voz clara") }
                        FilledTonalButton(
                            onClick = { onSetAudioProcessing(CreativeToolsUiPolicy.loudnessAudioPreset(settings)) },
                        ) { Text("Loudness") }
                        OutlinedButton(
                            onClick = { onSetAudioProcessing(AudioToolsUiPolicy.reset()) },
                            enabled = settings != AudioProcessingSettings(),
                        ) { Text("Reset") }
                    }
                }

                AudioToolsPanelSection.EQ -> {
                    Text("EQ paramétrico 3 bandas", style = MaterialTheme.typography.labelLarge)
                    AudioCommitSlider(
                        label = "Graves 120 Hz",
                        initial = AudioToolsUiPolicy.eqGain(settings, AudioEqBand.LOW),
                        range = -12f..12f,
                        suffix = " dB",
                    ) { value ->
                        onSetAudioProcessing(AudioToolsUiPolicy.setEqGain(settings, AudioEqBand.LOW, value))
                    }
                    AudioCommitSlider(
                        label = "Q graves",
                        initial = AudioToolsUiPolicy.eqQ(settings, AudioEqBand.LOW),
                        range = 0.1f..20f,
                    ) { value ->
                        onSetAudioProcessing(AudioToolsUiPolicy.setEqQ(settings, AudioEqBand.LOW, value))
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
                        label = "Q médios",
                        initial = AudioToolsUiPolicy.eqQ(settings, AudioEqBand.MID),
                        range = 0.1f..20f,
                    ) { value ->
                        onSetAudioProcessing(AudioToolsUiPolicy.setEqQ(settings, AudioEqBand.MID, value))
                    }
                    AudioCommitSlider(
                        label = "Agudos 8 kHz",
                        initial = AudioToolsUiPolicy.eqGain(settings, AudioEqBand.HIGH),
                        range = -12f..12f,
                        suffix = " dB",
                    ) { value ->
                        onSetAudioProcessing(AudioToolsUiPolicy.setEqGain(settings, AudioEqBand.HIGH, value))
                    }
                    AudioCommitSlider(
                        label = "Q agudos",
                        initial = AudioToolsUiPolicy.eqQ(settings, AudioEqBand.HIGH),
                        range = 0.1f..20f,
                    ) { value ->
                        onSetAudioProcessing(AudioToolsUiPolicy.setEqQ(settings, AudioEqBand.HIGH, value))
                    }
                }

                AudioToolsPanelSection.DYNAMICS -> {
                    AudioToggle(
                        label = "Compressor",
                        checked = settings.compressor != null,
                        onCheckedChange = { enabled ->
                            onSetAudioProcessing(AudioToolsUiPolicy.setCompressorEnabled(settings, enabled))
                        },
                    )
                    settings.compressor?.let { compressor ->
                        AudioCommitSlider(
                            label = "Threshold",
                            initial = compressor.thresholdDb,
                            range = -80f..0f,
                            suffix = " dB",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setCompressorThreshold(settings, value)) }
                        AudioCommitSlider(
                            label = "Ratio",
                            initial = compressor.ratio,
                            range = 1f..100f,
                            suffix = ":1",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setCompressorRatio(settings, value)) }
                        AudioCommitSlider(
                            label = "Attack",
                            initial = compressor.attackMs,
                            range = 0.1f..1_000f,
                            suffix = " ms",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setCompressorAttack(settings, value)) }
                        AudioCommitSlider(
                            label = "Release",
                            initial = compressor.releaseMs,
                            range = 1f..5_000f,
                            suffix = " ms",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setCompressorRelease(settings, value)) }
                        AudioCommitSlider(
                            label = "Makeup",
                            initial = compressor.makeupGainDb,
                            range = -24f..24f,
                            suffix = " dB",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setCompressorMakeupGain(settings, value)) }
                    }

                    AudioToggle(
                        label = "Limiter",
                        checked = settings.limiter != null,
                        onCheckedChange = { enabled ->
                            onSetAudioProcessing(AudioToolsUiPolicy.setLimiterEnabled(settings, enabled))
                        },
                    )
                    settings.limiter?.let { limiter ->
                        AudioCommitSlider(
                            label = "Ceiling",
                            initial = limiter.ceilingDb,
                            range = -24f..0f,
                            suffix = " dB",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setLimiterCeiling(settings, value)) }
                    }

                    AudioToggle(
                        label = "Noise gate",
                        checked = settings.noiseGate != null,
                        onCheckedChange = { enabled ->
                            onSetAudioProcessing(AudioToolsUiPolicy.setNoiseGateEnabled(settings, enabled))
                        },
                    )
                    settings.noiseGate?.let { gate ->
                        AudioCommitSlider(
                            label = "Threshold",
                            initial = gate.thresholdDb,
                            range = -100f..0f,
                            suffix = " dB",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setNoiseGateThreshold(settings, value)) }
                        AudioCommitSlider(
                            label = "Attack",
                            initial = gate.attackMs,
                            range = 0.1f..1_000f,
                            suffix = " ms",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setNoiseGateAttack(settings, value)) }
                        AudioCommitSlider(
                            label = "Release",
                            initial = gate.releaseMs,
                            range = 1f..5_000f,
                            suffix = " ms",
                        ) { value -> onSetAudioProcessing(AudioToolsUiPolicy.setNoiseGateRelease(settings, value)) }
                    }
                }
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
