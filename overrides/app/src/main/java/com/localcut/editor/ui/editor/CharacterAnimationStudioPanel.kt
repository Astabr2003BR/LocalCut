package com.localcut.editor.ui.editor

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.domain.animation.BrowEmotion
import com.localcut.editor.domain.animation.BrowExpressionPresets
import com.localcut.editor.domain.animation.BrowExpressionSettings
import com.localcut.editor.domain.animation.GuidedVisemeOverlay
import com.localcut.editor.domain.animation.VisemeMouthDemo
import com.localcut.editor.domain.animation.PortraitMotionPreset
import com.localcut.editor.domain.animation.PortraitMotionPresets
import com.localcut.editor.domain.animation.PortraitMotionSettings
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.EffectInstance
import com.localcut.editor.domain.model.Project
import java.util.Locale

/**
 * Single mobile-first entry point for offline portrait animation. Parameter
 * variations and saved presets never download executable code or alter assets.
 * Changes are dispatched once, with one history/undo entry per Apply.
 */
@Composable
internal fun CharacterAnimationStudioPanel(
    project: Project,
    selectedClipId: String?,
    onSetEffects: (List<EffectInstance>) -> Unit,
) {
    val clip = selectedClipId?.let(project::findClip) ?: return
    val asset = project.assets.firstOrNull { it.id == clip.assetId } ?: return
    if (asset.type != AssetType.IMAGE) return

    val locked = project.tracks.any { track ->
        (track.locked || track.hidden) && track.clips.any { it.id == clip.id }
    }
    val current = clip.effects.firstOrNull { it.typeId == PortraitMotionPresets.TYPE_ID }
    val restored = remember(clip.id, current) {
        current?.let(PortraitMotionPresets::fromPersistedEffectOrNull)
    }
    var settings by remember(clip.id, current) {
        mutableStateOf(restored ?: PortraitMotionSettings())
    }
    var eyesVerified by remember(clip.id, current) {
        mutableStateOf(restored != null)
    }
    var showGeometry by remember(clip.id) { mutableStateOf(false) }
    var showAdvancedMotion by remember(clip.id) { mutableStateOf(false) }
    var mouthX by remember(clip.id) { mutableStateOf(0.51) }
    var mouthY by remember(clip.id) { mutableStateOf(0.49) }
    var mouthRadiusX by remember(clip.id) { mutableStateOf(0.07) }
    var mouthRadiusY by remember(clip.id) { mutableStateOf(0.025) }
    var mouthVerified by remember(clip.id) { mutableStateOf(false) }
    var voiceTranscript by remember(clip.id) { mutableStateOf("") }
    var showTimedCues by remember(clip.id) { mutableStateOf(false) }
    var timedCueText by remember(clip.id) { mutableStateOf("") }
    var timedCuesConfirmed by remember(clip.id, clip.effects) { mutableStateOf(false) }
    val browCurrent = clip.effects.firstOrNull { it.typeId == BrowExpressionPresets.TYPE_ID }
    val savedBrows = remember(clip.id, browCurrent) {
        BrowExpressionPresets.fromEffectOrNull(browCurrent)
    }
    var brows by remember(clip.id, browCurrent) {
        mutableStateOf(savedBrows ?: BrowExpressionSettings())
    }
    var browsVerified by remember(clip.id, browCurrent) {
        mutableStateOf(savedBrows != null)
    }
    val hasTalk = clip.effects.any { it.typeId == VisemeMouthDemo.TYPE_ID }
    val hasGuidedVisemes = clip.effects.any(GuidedVisemeOverlay::hasGuidance)
    val hasVisemeDemo = clip.effects.any {
        it.typeId == VisemeMouthDemo.TYPE_ID && it.id.startsWith(VisemeMouthDemo.ID_PREFIX)
    }
    var notice by remember(clip.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val storage = remember(context) {
        context.getSharedPreferences("localcut_portrait_motion_v1", Context.MODE_PRIVATE)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Estúdio de animação do personagem", style = MaterialTheme.typography.titleSmall)
        Text(
            "Ajuste movimentos no próprio APK. A voz e o TALK existentes serão preservados. " +
                "As configurações ficam no projeto e os seus presets ficam neste aparelho.",
            style = MaterialTheme.typography.bodySmall,
        )

        var showFacePicker by remember(clip.id, asset.fingerprint) { mutableStateOf(false) }
        OutlinedButton(onClick = { showFacePicker = !showFacePicker }) {
            Text(if (showFacePicker) "Ocultar configuração facial" else "Configurar rosto na imagem")
        }
        if (showFacePicker) {
            PortraitFacePicker(asset, settings, brows, !locked) { suggestion ->
                settings = suggestion.motion
                brows = suggestion.brows
                eyesVerified = false
                browsVerified = false
                showGeometry = true
                if (!hasTalk) {
                    mouthX = suggestion.mouth.centerX
                    mouthY = suggestion.mouth.centerY
                    mouthRadiusX = suggestion.mouth.radiusX
                    mouthRadiusY = suggestion.mouth.radiusY
                    mouthVerified = false
                }
                notice = if (hasTalk) "Sugestão de olhos e sobrancelhas; TALK existente preservado."
                    else "Sugestão facial pronta. Confira as regiões antes de aplicar."
            }
        }

        Text("Movimento de boca", style = MaterialTheme.typography.labelLarge)
        AudioLipSyncActionControl(modifier = Modifier.fillMaxWidth())

        Text("Visemas 2D — demonstração visual", style = MaterialTheme.typography.labelLarge)
        Text(
            "Teste as formas de boca A, E/I, O, U, F/V, M/B/P, L e S/T. " +
                "Este teste não identifica fonemas nem segue automaticamente o áudio. " +
                "Um TALK existente nunca será substituído.",
            style = MaterialTheme.typography.bodySmall,
        )
        MotionValueSlider("Boca: posição X", mouthX, 0.12f..0.88f, percent = true) {
            mouthX = it; mouthVerified = false
        }
        MotionValueSlider("Boca: posição Y", mouthY, 0.12f..0.88f, percent = true) {
            mouthY = it; mouthVerified = false
        }
        MotionValueSlider("Boca: largura", mouthRadiusX, 0.02f..0.20f, percent = true) {
            mouthRadiusX = it; mouthVerified = false
        }
        MotionValueSlider("Boca: altura", mouthRadiusY, 0.01f..0.10f, percent = true) {
            mouthRadiusY = it; mouthVerified = false
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = mouthVerified,
                onCheckedChange = { mouthVerified = it },
                enabled = !locked && !hasTalk,
            )
            Text(
                "Conferi a posição da boca no retrato.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    runCatching {
                        val updated = VisemeMouthDemo.apply(
                            clip.id, clip.durationUs, clip.effects,
                            mouthX, mouthY, mouthRadiusX, mouthRadiusY,
                        )
                        if (updated != clip.effects) {
                            onSetEffects(updated)
                            notice = "Visemas aplicados; reproduza e exporte para conferir."
                        }
                    }.onFailure { failure ->
                        notice = "Visemas não aplicados: " + (failure.message ?: "entrada inválida")
                    }
                },
                enabled = !locked && mouthVerified && !hasTalk &&
                    clip.durationUs >= VisemeMouthDemo.MIN_DURATION_US,
            ) { Text("Testar formatos de boca") }
            OutlinedButton(
                onClick = {
                    onSetEffects(VisemeMouthDemo.remove(clip.effects))
                    notice = "Demonstração removida; outros efeitos preservados."
                },
                enabled = !locked && hasVisemeDemo,
            ) { Text("Remover teste") }
        }
        if (hasTalk && !hasVisemeDemo) {
            Text("Este clipe já tem TALK. Use uma cópia do clipe para a demonstração.",
                style = MaterialTheme.typography.bodySmall)
        }

        Text("Articulação guiada pelo texto da voz", style = MaterialTheme.typography.labelLarge)
        Text(
            "Escreva exatamente a fala do áudio já sincronizado com TALK. " +
                "Os formatos de boca serão distribuídos aproximadamente entre os momentos de voz, " +
                "sem reconhecer o som. O áudio e a curva de abertura original serão preservados; " +
                "a articulação pode fechar a boca nos sons M/B/P.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = voiceTranscript,
            onValueChange = { if (it.length <= 160) voiceTranscript = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto falado (até 160 caracteres)") },
            minLines = 2,
            maxLines = 4,
            enabled = !locked && hasTalk && !hasVisemeDemo,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    runCatching {
                        val updated = GuidedVisemeOverlay.apply(
                            clip.durationUs, clip.effects, voiceTranscript,
                        )
                        if (updated == clip.effects) {
                            notice = "Articulação já aplicada; nenhuma duplicata."
                        } else {
                            onSetEffects(updated)
                            notice = "Formas labiais aplicadas à voz atual. Confira a prévia e exporte."
                        }
                    }.onFailure { failure ->
                        notice = "Texto não aplicado: " + (failure.message ?: "dados inválidos")
                    }
                },
                enabled = !locked && hasTalk && !hasVisemeDemo &&
                    voiceTranscript.isNotBlank() && clip.durationUs > 0L,
            ) { Text("Aplicar à fala existente") }
            OutlinedButton(
                onClick = {
                    val updated = GuidedVisemeOverlay.remove(clip.effects)
                    if (updated != clip.effects) {
                        onSetEffects(updated)
                        notice = "Formas guiadas removidas. TALK original preservado."
                    }
                },
                enabled = !locked && hasGuidedVisemes,
            ) { Text("Remover formas guiadas") }
        }
        OutlinedButton(onClick = { showTimedCues = !showTimedCues }) {
            Text(if (showTimedCues) "Ocultar visemas temporizados" else "Visemas temporizados (avançado)")
        }
        if (showTimedCues) {
            Text("Cole o TSV de um alinhador Rhubarb para a mesma fala, começando no " +
                "início do clipe. Use segundos, tabulação e código A–H ou X. " +
                "O último registro é o fim da fala. Não analisa áudio neste aparelho.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = timedCueText,
                onValueChange = {
                    if (it.length <= com.localcut.editor.domain.animation.RhubarbCueImport.MAX_TEXT_LENGTH) {
                        timedCueText = it
                        timedCuesConfirmed = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Visemas TSV") },
                minLines = 3,
                maxLines = 5,
                enabled = !locked && hasTalk && !hasVisemeDemo,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = timedCuesConfirmed, onCheckedChange = { timedCuesConfirmed = it },
                    enabled = !locked && hasTalk && !hasVisemeDemo)
                Text("Conferi que os tempos correspondem ao áudio deste clipe.",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(
                onClick = {
                    runCatching {
                        val cues = com.localcut.editor.domain.animation.RhubarbCueImport.parse(
                            timedCueText, clip.durationUs)
                        val updated = GuidedVisemeOverlay.applyTimed(clip.durationUs, clip.effects, cues)
                        if (updated != clip.effects) onSetEffects(updated)
                        notice = "Visemas temporizados aplicados. Confira a prévia."
                    }.onFailure { notice = "Importação recusada: " + (it.message ?: "dados inválidos") }
                },
                enabled = !locked && hasTalk && !hasVisemeDemo &&
                    timedCuesConfirmed && timedCueText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Aplicar visemas temporizados") }
        }
        if (!hasTalk) {
            Text("Para sincronizar com a voz, primeiro aplique TALK ao clipe de imagem.",
                style = MaterialTheme.typography.bodySmall)
        }

        if (current != null && restored == null) {
            Text(
                "A animação salva contém parâmetros inválidos. Ela não foi carregada neste painel. " +
                    "Revise a posição dos olhos e aplique novamente, ou remova os movimentos.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("Animação do retrato", style = MaterialTheme.typography.labelLarge)
        Text("Aplicação conjunta: confira olhos e sobrancelhas, escolha a expressão e " +
            "opcionalmente informe a fala. Uma única alteração, com Desfazer.",
            style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(
            onClick = {
                runCatching {
                    val motion = PortraitMotionPresets.preset(PortraitMotionPreset.NATURAL, settings)
                    val combined = com.localcut.editor.domain.animation.PortraitStudioBatch.apply(
                        clip.id, clip.durationUs, clip.effects, motion, brows,
                        voiceTranscript.takeIf { it.isNotBlank() && hasTalk && !hasVisemeDemo },
                    )
                    if (combined != clip.effects) onSetEffects(combined)
                    notice = "Estúdio aplicado. Confira a prévia; Desfazer preserva o estado anterior."
                }.onFailure { notice = "Estúdio não aplicado: " + (it.message ?: "entrada inválida") }
            },
            enabled = !locked && eyesVerified && browsVerified,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Aplicar conjunto natural") }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PortraitMotionPreset.entries.forEach { preset ->
                FilledTonalButton(
                    onClick = {
                        settings = PortraitMotionPresets.preset(preset, settings)
                        notice = "Preset ${preset.title}: confira olhos e toque Aplicar."
                    },
                    enabled = !locked,
                ) { Text(preset.title) }
            }
        }
        MotionValueSlider(
            "Intervalo das piscadas (s)", settings.intervalSeconds, 2f..6f,
        ) { settings = settings.copy(intervalSeconds = it) }
        MotionValueSlider(
            "Duração da piscada (s)", settings.durationSeconds, 0.10f..0.35f,
        ) { settings = settings.copy(durationSeconds = it) }
        MotionValueSlider(
            "Intensidade da piscada (%)", settings.blinkStrength, 0f..1f, percent = true,
        ) { settings = settings.copy(blinkStrength = it) }
        MotionValueSlider(
            "Movimento corporal sutil (%)", settings.breathStrength, 0f..1f, percent = true,
        ) { settings = settings.copy(breathStrength = it) }
        MotionValueSlider(
            "Oscilação lateral leve (%)", settings.swayStrength, 0f..1f, percent = true,
        ) { settings = settings.copy(swayStrength = it) }

        OutlinedButton(onClick = { showAdvancedMotion = !showAdvancedMotion }) {
            Text(if (showAdvancedMotion) "Ocultar movimentos avançados"
                else "Movimentos avançados do rosto")
        }
        if (showAdvancedMotion) {
            MotionValueSlider(
                "Movimento suave da cabeça (%)", settings.headMotionStrength, 0f..1f, percent = true,
            ) { settings = settings.copy(headMotionStrength = it) }
            MotionValueSlider(
                "Movimento leve do cabelo (%)", settings.hairMotionStrength, 0f..1f, percent = true,
            ) { settings = settings.copy(hairMotionStrength = it) }
            MotionValueSlider(
                "Variação natural das piscadas (%)", settings.blinkVariation, 0f..1f, percent = true,
            ) { settings = settings.copy(blinkVariation = it) }
            MotionValueSlider(
                "Assimetria suave dos olhos (%)", settings.blinkAsymmetry, 0f..1f, percent = true,
            ) { settings = settings.copy(blinkAsymmetry = it) }
            MotionValueSlider(
                "Movimento discreto do olhar (%)", settings.gazeStrength, 0f..1f, percent = true,
            ) { settings = settings.copy(gazeStrength = it) }
            Text("Movimentos leves por regiões da imagem; não fazem uma cabeça 3D real.",
                style = MaterialTheme.typography.bodySmall)
        }

        Text("Expressões 2D — sobrancelhas", style = MaterialTheme.typography.labelLarge)
        Text(
            "Escolha uma expressão e ajuste a posição das sobrancelhas na imagem. " +
                "O efeito deforma apenas essa região e não detecta rostos automaticamente. " +
                "Confira as duas sobrancelhas antes de aplicar.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BrowEmotion.entries.forEach { emotion ->
                FilledTonalButton(
                    onClick = {
                        brows = brows.copy(emotion = emotion)
                        notice = "Expressão ${emotion.title} selecionada. Confira a posição."
                    },
                    enabled = !locked,
                ) { Text(if (brows.emotion == emotion) "✓ ${emotion.title}" else emotion.title) }
            }
        }
        MotionValueSlider(
            "Intensidade da expressão (%)", brows.intensity, 0f..1f, percent = true,
        ) { brows = brows.copy(intensity = it) }
        MotionValueSlider(
            "Sobrancelha esquerda X", brows.leftX, 0.16f..0.46f, percent = true,
        ) { brows = brows.copy(leftX = it); browsVerified = false }
        MotionValueSlider(
            "Sobrancelha direita X", brows.rightX, 0.54f..0.84f, percent = true,
        ) { brows = brows.copy(rightX = it); browsVerified = false }
        MotionValueSlider(
            "Altura das sobrancelhas Y", brows.browY, 0.12f..0.48f, percent = true,
        ) { brows = brows.copy(browY = it); browsVerified = false }
        MotionValueSlider(
            "Largura da região das sobrancelhas", brows.radiusX, 0.02f..0.15f,
            percent = true,
        ) { brows = brows.copy(radiusX = it); browsVerified = false }
        MotionValueSlider(
            "Altura da região das sobrancelhas", brows.radiusY, 0.01f..0.09f,
            percent = true,
        ) { brows = brows.copy(radiusY = it); browsVerified = false }
        Row(modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = browsVerified,
                onCheckedChange = { browsVerified = it },
                enabled = !locked,
            )
            Text("Conferi a posição das sobrancelhas.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    runCatching {
                        val proposed = BrowExpressionPresets.upsert(
                            clip.id, clip.effects, brows,
                        )
                        if (proposed == clip.effects) {
                            notice = "Expressão já aplicada; nenhuma duplicata."
                        } else {
                            onSetEffects(proposed)
                            notice = "Expressão aplicada. TALK e piscadas preservados."
                        }
                    }.onFailure { error ->
                        notice = "Expressão não aplicada: " + (error.message ?: "valores inválidos")
                    }
                },
                enabled = !locked && browsVerified,
            ) { Text("Aplicar expressão") }
            OutlinedButton(
                onClick = {
                    onSetEffects(BrowExpressionPresets.remove(clip.effects))
                    notice = "Expressão removida. TALK e outros movimentos preservados."
                },
                enabled = !locked && browCurrent != null,
            ) { Text("Remover expressão") }
        }
        if (browCurrent != null && savedBrows == null) {
            Text("Expressão salva inválida; revise antes de alterar ou remova o efeito.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(onClick = { showGeometry = !showGeometry }) {
            Text(if (showGeometry) "Ocultar posição dos olhos" else "Ajustar posição dos olhos")
        }
        if (showGeometry) {
            Text(
                "A posição é relativa à imagem original. Não é reconhecimento facial automático. " +
                    "Confira a posição dos dois olhos antes de aplicar.",
                style = MaterialTheme.typography.bodySmall,
            )
            MotionValueSlider("Olho esquerdo X", settings.leftX, 0.16f..0.46f, percent = true) {
                settings = settings.copy(leftX = it); eyesVerified = false
            }
            MotionValueSlider("Olho direito X", settings.rightX, 0.54f..0.84f, percent = true) {
                settings = settings.copy(rightX = it); eyesVerified = false
            }
            MotionValueSlider("Altura dos olhos Y", settings.eyeY, 0.15f..0.55f, percent = true) {
                settings = settings.copy(eyeY = it); eyesVerified = false
            }
            MotionValueSlider("Largura da região", settings.radiusX, 0.02f..0.15f, percent = true) {
                settings = settings.copy(radiusX = it); eyesVerified = false
            }
            MotionValueSlider("Altura da região", settings.radiusY, 0.01f..0.09f, percent = true) {
                settings = settings.copy(radiusY = it); eyesVerified = false
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = eyesVerified,
                onCheckedChange = { eyesVerified = it },
                enabled = !locked,
            )
            Text(
                "Conferi a posição dos olhos no retrato.",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (locked) Text(
            "Faixa visual bloqueada ou oculta. Não será alterada.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FilledTonalButton(
                onClick = {
                    runCatching {
                        val proposed = PortraitMotionPresets.upsert(
                            clip.id, clip.effects, settings,
                        )
                        if (proposed == clip.effects) {
                            notice = "Configuração já aplicada; nenhuma alteração duplicada."
                        } else {
                            onSetEffects(proposed)
                            notice = "Animação aplicada. Desfazer está disponível."
                        }
                    }.onFailure { notice = "Não aplicado: ${it.message ?: "valores inválidos"}" }
                },
                enabled = !locked && eyesVerified,
            ) { Text("Aplicar animação") }

            OutlinedButton(
                onClick = {
                    if (current != null) {
                        onSetEffects(PortraitMotionPresets.remove(clip.effects))
                        notice = "Piscadas e movimentos removidos; TALK e áudio preservados."
                    }
                },
                enabled = !locked && current != null,
            ) { Text("Remover movimentos") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        val encoded = PortraitMotionPresets.serialize(settings)
                        storage.edit().putString("custom", encoded).apply()
                        notice = "Meu preset salvo no dispositivo."
                    }.onFailure { notice = "Preset não salvo: valores inválidos" }
                },
            ) { Text("Salvar meu preset") }
            OutlinedButton(
                onClick = {
                    val restored = storage.getString("custom", null)?.let {
                        PortraitMotionPresets.deserialize(it)
                    }
                    if (restored != null) {
                        settings = restored
                        eyesVerified = false
                        notice = "Preset carregado; confira os olhos e aplique."
                    } else {
                        notice = "Nenhum preset válido salvo neste aparelho."
                    }
                },
            ) { Text("Carregar preset") }
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun MotionValueSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    percent: Boolean = false,
    onChange: (Double) -> Unit,
) {
    val display = if (percent) "${(value * 100.0).toInt()}%"
        else String.format(Locale.ROOT, "%.2f", value)
    Text("$label: $display", style = MaterialTheme.typography.labelSmall)
    Slider(
        value = value.toFloat().coerceIn(range.start, range.endInclusive),
        onValueChange = { onChange(it.toDouble()) },
        valueRange = range,
    )
}
