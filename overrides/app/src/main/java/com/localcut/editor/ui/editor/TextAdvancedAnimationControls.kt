package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.model.TextAnimationDescriptor
import com.localcut.editor.domain.model.TextAnimationKind
import kotlin.math.roundToInt

@Composable
internal fun TextAdvancedAnimationControls(
    model: TextOverlayEditorModel,
    onModelChange: (TextOverlayEditorModel) -> Unit,
) {
    val draft = model.draft
    val hasAnimation = listOf(draft.entrance, draft.exit, draft.loop).any { it.kind != TextAnimationKind.NONE }
    if (!hasAnimation) return

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Ajuste de animação", style = MaterialTheme.typography.labelLarge)

        if (draft.entrance.kind != TextAnimationKind.NONE) {
            TextAnimationDescriptorControls(
                label = "Entrada",
                descriptor = draft.entrance,
                onDurationUs = { onModelChange(model.withEntranceDurationUs(it)) },
                onAmount = { onModelChange(model.withEntranceAmount(it)) },
            )
            if (draft.entrance.kind == TextAnimationKind.TYPEWRITER) {
                val delayMs = draft.perCharacterDelayUs / 1_000.0
                AnimationValueSlider(
                    label = if (draft.perCharacterDelayUs == 0L) "Caractere Auto" else "Caractere ${delayMs.roundToInt()} ms",
                    value = draft.perCharacterDelayUs.toDouble(),
                    range = 0.0..1_000_000.0,
                    onValueChange = { onModelChange(model.withPerCharacterDelayUs(it.toLong())) },
                )
            }
        }

        if (draft.exit.kind != TextAnimationKind.NONE) {
            TextAnimationDescriptorControls(
                label = "Saída",
                descriptor = draft.exit,
                onDurationUs = { onModelChange(model.withExitDurationUs(it)) },
                onAmount = { onModelChange(model.withExitAmount(it)) },
            )
        }

        if (draft.loop.kind != TextAnimationKind.NONE) {
            TextAnimationDescriptorControls(
                label = "Loop",
                descriptor = draft.loop,
                onDurationUs = { onModelChange(model.withLoopDurationUs(it)) },
                onAmount = { onModelChange(model.withLoopAmount(it)) },
            )
        }
    }
}

@Composable
private fun TextAnimationDescriptorControls(
    label: String,
    descriptor: TextAnimationDescriptor,
    onDurationUs: (Long) -> Unit,
    onAmount: (Double) -> Unit,
) {
    val seconds = descriptor.durationUs / 1_000_000.0
    AnimationValueSlider(
        label = "$label ${"%.2f".format(seconds)} s",
        value = descriptor.durationUs.toDouble(),
        range = 50_000.0..5_000_000.0,
        onValueChange = { onDurationUs(it.toLong()) },
    )

    if (descriptor.kind.usesAmount()) {
        AnimationValueSlider(
            label = "Intensidade ${"%.2f".format(descriptor.amount)}×",
            value = descriptor.amount,
            range = 0.0..3.0,
            onValueChange = onAmount,
        )
    }
}

@Composable
private fun AnimationValueSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.width(132.dp), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.toFloat().coerceIn(range.start.toFloat(), range.endInclusive.toFloat()),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun TextAnimationKind.usesAmount(): Boolean = when (this) {
    TextAnimationKind.SLIDE_UP,
    TextAnimationKind.SLIDE_DOWN,
    TextAnimationKind.SLIDE_LEFT,
    TextAnimationKind.SLIDE_RIGHT,
    TextAnimationKind.BOUNCE,
    TextAnimationKind.WIGGLE -> true
    TextAnimationKind.POP,
    TextAnimationKind.SCALE -> true
    TextAnimationKind.NONE,
    TextAnimationKind.FADE,
    TextAnimationKind.TYPEWRITER -> false
}
