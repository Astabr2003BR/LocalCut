package com.localcut.editor.ui.editor.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TransformKeyframe
import com.localcut.editor.domain.model.TransformValue
import com.localcut.editor.ui.editor.KeyframeEditorPolicy
import kotlin.math.max
import kotlin.math.min

private enum class InspectorSection {
    TRANSFORM,
    KEYFRAMES,
    AUDIO,
}

@Composable
fun InspectorPane(
    project: Project,
    selectedClipId: String?,
    playheadUs: Long,
    onDelete: () -> Unit,
    onSplit: (Long) -> Unit,
    onTransform: (TransformValue) -> Unit,
    onSetKeyframes: (List<TransformKeyframe>) -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Double) -> Unit,
    onAudioFades: (Long, Long) -> Unit,
    onOpacity: (Double) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clip = selectedClipId?.let(project::findClip)
    val asset = clip?.let { c -> project.assets.firstOrNull { it.id == c.assetId } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (clip == null) {
            Text("Selecione um clipe", style = MaterialTheme.typography.labelLarge)
            Text(
                "Toque em um clipe na timeline para editar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val visual = asset?.type != AssetType.AUDIO
        val sections = if (visual) {
            listOf(InspectorSection.TRANSFORM, InspectorSection.KEYFRAMES, InspectorSection.AUDIO)
        } else {
            listOf(InspectorSection.AUDIO)
        }
        var activeSection by remember(clip.id, asset?.type) {
            mutableStateOf(if (visual) InspectorSection.TRANSFORM else InspectorSection.AUDIO)
        }
        if (activeSection !in sections) activeSection = sections.first()

        Text(
            text = asset?.displayName ?: "Clipe",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Text(
            text = "${"%.2f".format(clip.durationUs / 1_000_000.0)} s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sections.forEach { section ->
                val label = when (section) {
                    InspectorSection.TRANSFORM -> "Transformar"
                    InspectorSection.KEYFRAMES -> "Keyframes"
                    InspectorSection.AUDIO -> "Áudio"
                }
                if (section == activeSection) {
                    FilledTonalButton(onClick = { activeSection = section }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { activeSection = section }) { Text(label) }
                }
            }
        }

        when (activeSection) {
            InspectorSection.TRANSFORM -> TransformInspector(
                project = project,
                clipId = clip.id,
                initialTransform = clip.transform,
                initialOpacity = clip.opacity,
                onTransform = onTransform,
                onOpacity = onOpacity,
            )

            InspectorSection.KEYFRAMES -> KeyframeInspector(
                project = project,
                clipId = clip.id,
                playheadUs = playheadUs,
                onSetKeyframes = onSetKeyframes,
                onSeek = onSeek,
            )

            InspectorSection.AUDIO -> AudioInspector(
                clipId = clip.id,
                volume = clip.volume,
                fadeInUs = clip.fadeInUs,
                fadeOutUs = clip.fadeOutUs,
                durationUs = clip.durationUs,
                supportsFades = asset?.type != AssetType.IMAGE,
                onVolume = onVolume,
                onAudioFades = onAudioFades,
            )
        }
    }
}

@Composable
private fun TransformInspector(
    project: Project,
    clipId: String,
    initialTransform: TransformValue,
    initialOpacity: Double,
    onTransform: (TransformValue) -> Unit,
    onOpacity: (Double) -> Unit,
) {
    var transformDraft by remember(clipId, initialTransform) { mutableStateOf(initialTransform) }
    var opacityDraft by remember(clipId, initialOpacity) {
        mutableFloatStateOf(initialOpacity.coerceIn(0.0, 1.0).toFloat())
    }

    CompactSlider(
        label = "X ${"%.0f".format(transformDraft.x)} px",
        value = transformDraft.x.toFloat().coerceIn(-project.width.toFloat(), project.width.toFloat()),
        range = -project.width.toFloat()..project.width.toFloat(),
        onValueChange = { transformDraft = transformDraft.copy(x = it.toDouble()) },
        onFinished = { onTransform(transformDraft) },
    )
    CompactSlider(
        label = "Y ${"%.0f".format(transformDraft.y)} px",
        value = transformDraft.y.toFloat().coerceIn(-project.height.toFloat(), project.height.toFloat()),
        range = -project.height.toFloat()..project.height.toFloat(),
        onValueChange = { transformDraft = transformDraft.copy(y = it.toDouble()) },
        onFinished = { onTransform(transformDraft) },
    )
    CompactSlider(
        label = "Escala ${"%.2f".format(transformDraft.scale)}×",
        value = transformDraft.scale.toFloat().coerceIn(0.05f, 4f),
        range = 0.05f..4f,
        onValueChange = { transformDraft = transformDraft.copy(scale = it.toDouble()) },
        onFinished = { onTransform(transformDraft) },
    )
    CompactSlider(
        label = "Rotação ${"%.0f".format(transformDraft.rotationDeg)}°",
        value = transformDraft.rotationDeg.toFloat().coerceIn(-180f, 180f),
        range = -180f..180f,
        onValueChange = { transformDraft = transformDraft.copy(rotationDeg = it.toDouble()) },
        onFinished = { onTransform(transformDraft) },
    )
    CompactSlider(
        label = "Opacidade ${"%.0f".format(opacityDraft * 100f)}%",
        value = opacityDraft,
        range = 0f..1f,
        onValueChange = { opacityDraft = it },
        onFinished = { onOpacity(opacityDraft.toDouble()) },
    )

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(onClick = {
            val next = transformDraft.copy(scale = (transformDraft.scale - 0.05).coerceAtLeast(0.05))
            transformDraft = next
            onTransform(next)
        }) { Text("Zoom −") }
        OutlinedButton(onClick = {
            val next = transformDraft.copy(scale = (transformDraft.scale + 0.05).coerceAtMost(4.0))
            transformDraft = next
            onTransform(next)
        }) { Text("Zoom +") }
        OutlinedButton(onClick = {
            val next = transformDraft.copy(rotationDeg = (transformDraft.rotationDeg - 5.0).coerceAtLeast(-180.0))
            transformDraft = next
            onTransform(next)
        }) { Text("↺ 5°") }
        OutlinedButton(onClick = {
            val next = transformDraft.copy(rotationDeg = (transformDraft.rotationDeg + 5.0).coerceAtMost(180.0))
            transformDraft = next
            onTransform(next)
        }) { Text("↻ 5°") }
        OutlinedButton(onClick = {
            transformDraft = TransformValue()
            onTransform(transformDraft)
        }) { Text("Reset") }
    }
}

@Composable
private fun KeyframeInspector(
    project: Project,
    clipId: String,
    playheadUs: Long,
    onSetKeyframes: (List<TransformKeyframe>) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val clip = project.findClip(clipId) ?: return
    val localOffsetUs = KeyframeEditorPolicy.localOffsetUs(clip, playheadUs)
    val valueAtCursor = KeyframeEditorPolicy.valueAtProjectTime(clip, playheadUs)
    var draft by remember(clip.id, playheadUs, clip.keyframes, clip.transform) {
        mutableStateOf(valueAtCursor ?: clip.transform)
    }
    val exact = localOffsetUs != null && clip.keyframes.any { it.offsetUs == localOffsetUs }
    val previousUs = KeyframeEditorPolicy.previousProjectTimeUs(clip, playheadUs)
    val nextUs = KeyframeEditorPolicy.nextProjectTimeUs(clip, playheadUs)
    val insideClip = localOffsetUs != null

    Text(
        text = "${clip.keyframes.size} keyframes${localOffsetUs?.let { " • ${"%.2f".format(it / 1_000_000.0)} s" } ?: ""}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    CompactSlider(
        label = "X ${"%.0f".format(draft.x)} px",
        value = draft.x.toFloat().coerceIn(-project.width.toFloat(), project.width.toFloat()),
        range = -project.width.toFloat()..project.width.toFloat(),
        enabled = insideClip,
        onValueChange = { draft = draft.copy(x = it.toDouble()) },
    )
    CompactSlider(
        label = "Y ${"%.0f".format(draft.y)} px",
        value = draft.y.toFloat().coerceIn(-project.height.toFloat(), project.height.toFloat()),
        range = -project.height.toFloat()..project.height.toFloat(),
        enabled = insideClip,
        onValueChange = { draft = draft.copy(y = it.toDouble()) },
    )
    CompactSlider(
        label = "Escala ${"%.2f".format(draft.scale)}×",
        value = draft.scale.toFloat().coerceIn(0.05f, 4f),
        range = 0.05f..4f,
        enabled = insideClip,
        onValueChange = { draft = draft.copy(scale = it.toDouble()) },
    )
    CompactSlider(
        label = "Rotação ${"%.0f".format(draft.rotationDeg)}°",
        value = draft.rotationDeg.toFloat().coerceIn(-180f, 180f),
        range = -180f..180f,
        enabled = insideClip,
        onValueChange = { draft = draft.copy(rotationDeg = it.toDouble()) },
    )
    CompactSlider(
        label = "Opacidade ${"%.0f".format(draft.opacity * 100)}%",
        value = draft.opacity.toFloat().coerceIn(0f, 1f),
        range = 0f..1f,
        enabled = insideClip,
        onValueChange = { draft = draft.copy(opacity = it.toDouble()) },
    )

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilledTonalButton(
            onClick = {
                KeyframeEditorPolicy.upsertAtProjectTime(clip, playheadUs, draft)?.let(onSetKeyframes)
            },
            enabled = insideClip,
        ) { Text(if (exact) "Atualizar" else "+ Keyframe") }
        OutlinedButton(onClick = { previousUs?.let(onSeek) }, enabled = previousUs != null) { Text("‹") }
        OutlinedButton(onClick = { nextUs?.let(onSeek) }, enabled = nextUs != null) { Text("›") }
        Button(
            onClick = { KeyframeEditorPolicy.deleteAtProjectTime(clip, playheadUs)?.let(onSetKeyframes) },
            enabled = exact,
        ) { Text("Excluir KF") }
    }
}

@Composable
private fun AudioInspector(
    clipId: String,
    volume: Double,
    fadeInUs: Long,
    fadeOutUs: Long,
    durationUs: Long,
    supportsFades: Boolean,
    onVolume: (Double) -> Unit,
    onAudioFades: (Long, Long) -> Unit,
) {
    var volumeDraft by remember(clipId, volume) {
        mutableFloatStateOf(volume.coerceIn(0.0, 2.0).toFloat())
    }
    CompactSlider(
        label = "Volume ${"%.0f".format(volumeDraft * 100)}%",
        value = volumeDraft,
        range = 0f..2f,
        onValueChange = { volumeDraft = it },
        onFinished = { onVolume(volumeDraft.toDouble()) },
    )

    if (!supportsFades) return

    val durationSeconds = max(0.001f, durationUs / 1_000_000f)
    var fadeInDraft by remember(clipId, fadeInUs) {
        mutableFloatStateOf((fadeInUs / 1_000_000f).coerceIn(0f, durationSeconds))
    }
    var fadeOutDraft by remember(clipId, fadeOutUs) {
        mutableFloatStateOf((fadeOutUs / 1_000_000f).coerceIn(0f, durationSeconds))
    }
    val maxFadeIn = max(0f, durationSeconds - fadeOutDraft)
    val maxFadeOut = max(0f, durationSeconds - fadeInDraft)

    CompactSlider(
        label = "Fade in ${"%.2f".format(fadeInDraft)} s",
        value = fadeInDraft.coerceIn(0f, max(0.001f, maxFadeIn)),
        range = 0f..max(0.001f, maxFadeIn),
        onValueChange = { fadeInDraft = min(it, durationSeconds - fadeOutDraft) },
        onFinished = {
            onAudioFades((fadeInDraft * 1_000_000f).toLong(), (fadeOutDraft * 1_000_000f).toLong())
        },
    )
    CompactSlider(
        label = "Fade out ${"%.2f".format(fadeOutDraft)} s",
        value = fadeOutDraft.coerceIn(0f, max(0.001f, maxFadeOut)),
        range = 0f..max(0.001f, maxFadeOut),
        onValueChange = { fadeOutDraft = min(it, durationSeconds - fadeInDraft) },
        onFinished = {
            onAudioFades((fadeInDraft * 1_000_000f).toLong(), (fadeOutDraft * 1_000_000f).toLong())
        },
    )
}

@Composable
private fun CompactSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            enabled = enabled,
        )
    }
}
