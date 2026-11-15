package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localcut.editor.bridge.agent.BridgeUiState
import com.localcut.editor.bridge.agent.BridgeJobRegistry
import com.localcut.editor.session.ProjectSession
import com.localcut.editor.session.ProjectSessionState

@Composable
internal fun EditorAiDialog(
    session: ProjectSession,
    state: ProjectSessionState,
    bridgeState: BridgeUiState,
    jobs: BridgeJobRegistry,
    busy: Boolean,
    onCopyContext: (String) -> Unit,
    onCreateVideo: () -> Unit,
    onApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val latestJob by jobs.latest.collectAsState()
    val report = remember(latestJob, state.project.id, state.revision) { EditorAiExportReview.from(latestJob, state.project.id, state.revision) }
    val gate = remember { EditorAiApprovalGate() }
    var draft by remember { mutableStateOf("") }
    var approval by remember { mutableStateOf<EditorAiApprovalGate.Approval?>(null) }
    var message by remember { mutableStateOf("") }
    val prepared = approval
    val stale = prepared != null && (prepared.projectId != state.project.id || prepared.revision != state.revision)
    AlertDialog(
        onDismissRequest = { gate.cancel(); onDismiss() },
        title = { Text("Editor IA") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Projeto atual: ${state.project.title.take(100)} • revisão ${state.revision}", style = MaterialTheme.typography.bodyMedium)
                Text(if (BridgePanelUiPolicy.agentReady(bridgeState)) "Bridge conectado. O GPT externo pode revisar a timeline quando o editor está aberto."
                    else "Bridge não está pronto. Você pode copiar o contexto e colar um plano do GPT manualmente.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onCopyContext(EditorAiApprovalGate.context(session.state)); message = "Contexto copiado. Envie ao GPT e cole o JSON retornado." },
                    enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Copiar contexto para GPT") }
                OutlinedButton(onClick = { gate.cancel(); onCreateVideo() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Criar novo vídeo / abrir pacote") }
                Text("Editar projeto atual: cole um plano visual. Não permite excluir mídias, mudar áudio ou duração. A alteração pode ser desfeita.")
                OutlinedTextField(value = draft, onValueChange = {
                    if (it.length <= EditorAiApprovalGate.MAX_BYTES && it.toByteArray(Charsets.UTF_8).size <= EditorAiApprovalGate.MAX_BYTES) {
                        draft = it; gate.cancel(); approval = null; message = ""
                    } else message = "Plano acima de 8 KiB."
                }, label = { Text("Plano JSON do GPT") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, enabled = !busy && prepared == null)
                if (prepared != null) {
                    Text("Plano validado • ${prepared.operationCount} operações", style = MaterialTheme.typography.titleSmall)
                    Text(prepared.summary, style = MaterialTheme.typography.bodySmall)
                    Text(if (stale) "A revisão mudou. Valide novamente." else "Confira o JSON acima antes de aprovar. A validação não alterou o projeto.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { gate.cancel(); approval = null; message = "" }) { Text("Revisar plano") }
                } else Button(onClick = {
                    gate.prepare(draft, session.state).onSuccess { approval = it; message = "" }
                        .onFailure { message = "Plano rejeitado: ${it.message?.take(180) ?: "confira o JSON"}" }
                }, enabled = !busy && draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Validar plano") }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                Text("Revisão do MP4 exportado", style = MaterialTheme.typography.titleSmall)
                Text(report.title, style = MaterialTheme.typography.bodyMedium)
                Text(report.detail, style = MaterialTheme.typography.bodySmall)
                report.request?.let { request ->
                    OutlinedButton(onClick = { onCopyContext(request); message = "Pedido de revisão copiado. Envie ao GPT conectado." },
                        enabled = !busy && BridgePanelUiPolicy.agentReady(bridgeState), modifier = Modifier.fillMaxWidth()) { Text("Copiar pedido de revisão visual") }
                }
                Text("Após aplicar, volte à timeline para conferir a prévia. O GPT externo analisa os quadros pelo Bridge; esta tela não executa uma IA local.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = prepared != null && !stale && !busy, onClick = {
                val accepted = approval ?: return@TextButton
                approval = null
                gate.apply(accepted, draft, session).onSuccess {
                    onApplied()
                    message = "Aplicado na revisão $it. Confira a prévia; Desfazer continua disponível."
                    draft = ""
                }.onFailure { message = it.message?.take(180) ?: "Não foi possível aplicar." }
            }) { Text("Aprovar e aplicar") }
        },
        dismissButton = { TextButton(onClick = { gate.cancel(); onDismiss() }) { Text("Fechar") } },
    )
}
