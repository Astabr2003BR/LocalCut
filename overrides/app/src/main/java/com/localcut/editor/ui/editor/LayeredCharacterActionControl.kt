package com.localcut.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.domain.animation.CharacterAction
import com.localcut.editor.domain.animation.CharacterRigBinding
import com.localcut.editor.domain.animation.LayeredCharacterActionPolicy
import com.localcut.editor.domain.history.ApplyAnimationPlanCommand
import com.localcut.editor.session.EditorAction

@Composable
internal fun LayeredCharacterActionControl() {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { LocalCutProcessRuntime.get(context) }
    val session by runtime.editorViewModel.sessionState.collectAsState()
    val project = session.project
    val bodyId = session.selectedClipId ?: return
    val candidates = LayeredCharacterActionPolicy.candidates(project, bodyId)
    if (candidates.none { it.id == bodyId }) return
    var open by remember(project.id, bodyId) { mutableStateOf(false) }
    var assigned by remember(project.id, bodyId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var action by remember(project.id, bodyId) { mutableStateOf(CharacterAction.IDLE) }
    var duration by remember(project.id, bodyId) { mutableStateOf(2f) }
    var intensity by remember(project.id, bodyId) { mutableStateOf(0.7f) }
    var confirmed by remember(project.id, bodyId, session.revision) { mutableStateOf(false) }
    var notice by remember(project.id, bodyId) { mutableStateOf<String?>(null) }
    val parts = listOf("head" to "Cabeça", "eyes" to "Olhos", "mouth" to "Boca",
        "la" to "Braço esquerdo", "ra" to "Braço direito", "ll" to "Perna esquerda", "rl" to "Perna direita")
    val labels = listOf(CharacterAction.IDLE to "Repouso", CharacterAction.WALK to "Passos no lugar",
        CharacterAction.POINT to "Apontar", CharacterAction.TALK to "Falar (pulso)")
    OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
        Text(if (open) "Ocultar personagem por camadas" else "Personagem por camadas")
    }
    if (!open) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("O clipe selecionado é o tronco. Importe partes recortadas com transparência em faixas visuais " +
            "sobrepostas, alinhadas no mesmo início. Posicione cada parte antes de animar. " +
            "Não recorta uma imagem inteira automaticamente nem cria esqueleto com IK.",
            style = MaterialTheme.typography.bodySmall)
        parts.forEach { (key, label) ->
            Text(label, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { assigned = assigned - key; confirmed = false }) {
                    Text(if (assigned[key] == null) "✓ Nenhum" else "Nenhum")
                }
                candidates.filter { it.id != bodyId && (it.id !in assigned.values || assigned[key] == it.id) }
                    .forEach { clip ->
                        val name = project.assets.firstOrNull { it.id == clip.assetId }?.displayName ?: "Imagem"
                        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clip.id } }
                        OutlinedButton(onClick = { assigned = assigned + (key to clip.id); confirmed = false }) {
                            Text((if (assigned[key] == clip.id) "✓ " else "") +
                                name.take(24) + " · faixa " + ((track?.order ?: 0) + 1))
                        }
                    }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEach { (value, label) ->
                OutlinedButton(onClick = { action = value; confirmed = false }) {
                    Text((if (action == value) "✓ " else "") + label)
                }
            }
        }
        Text("Duração: " + duration.toInt() + " s")
        Slider(value = duration, onValueChange = { duration = it; confirmed = false },
            valueRange = 1f..10f, steps = 8)
        Text("Intensidade: " + (intensity * 100).toInt() + "%")
        Slider(value = intensity, onValueChange = { intensity = it; confirmed = false },
            valueRange = 0.1f..1f)
        Text("A fala por pulso é apenas um movimento de demonstração, não sincronização de fonemas.",
            style = MaterialTheme.typography.bodySmall)
        Row {
            Checkbox(confirmed, { confirmed = it })
            Text("Conferi as camadas e o posicionamento. Posso desfazer a ação inteira.")
        }
        FilledTonalButton(onClick = {
            val before = runtime.session.state
            runCatching {
                require(before.project.id == project.id && before.revision == session.revision &&
                    before.selectedClipId == bodyId) { "Projeto ou seleção mudou" }
                val rig = CharacterRigBinding(bodyId, assigned["head"], assigned["eyes"], assigned["mouth"],
                    assigned["la"], assigned["ra"], assigned["ll"], assigned["rl"])
                val plan = LayeredCharacterActionPolicy.plan(before.project, rig, action,
                    duration.toLong() * 1_000_000L, intensity.toDouble())
                runtime.editorViewModel.dispatch(EditorAction.Execute(ApplyAnimationPlanCommand(plan)))
                check(runtime.session.state.revision == before.revision + 1L) {
                    runtime.session.state.lastError ?: "Ação não aplicada"
                }
                notice = "Movimento aplicado em " + plan.patches.size + " camadas. Use Desfazer para reverter."
                confirmed = false
            }.onFailure { notice = "Não aplicado: " + (it.message ?: "dados inválidos") }
        }, enabled = confirmed && assigned.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Animar camadas")
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
