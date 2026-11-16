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
import androidx.compose.runtime.DisposableEffect
import com.localcut.editor.bridge.agent.BridgeReviewProposal
import com.localcut.editor.bridge.agent.ReviewHistoryStatus
import com.localcut.editor.bridge.agent.ReviewHistoryView
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
    val incomingProposal by jobs.reviewProposal.collectAsState()
    val report = remember(latestJob, state.project.id, state.revision) { EditorAiExportReview.from(latestJob, state.project.id, state.revision) }
    val gate = remember { EditorAiApprovalGate() }
    var draft by remember { mutableStateOf("") }
    var approval by remember { mutableStateOf<EditorAiApprovalGate.Approval?>(null) }
    var message by remember { mutableStateOf("") }
    var loadedProposal by remember { mutableStateOf<BridgeReviewProposal?>(null) }
    val history = jobs.reviewHistory
    val historyView = history?.view?.collectAsState()?.value ?: ReviewHistoryView()
    fun finishLoaded(status: ReviewHistoryStatus, revision: Long = -1L) {
        loadedProposal?.let { history?.transition(it.id, status, revision) }
        loadedProposal = null
    }
    DisposableEffect(Unit) {
        onDispose { finishLoaded(ReviewHistoryStatus.DISCARDED) }
    }
    val prepared = approval
    val stale = prepared != null && (prepared.projectId != state.project.id || prepared.revision != state.revision ||
        (prepared.reviewSource != null && prepared.reviewSource != report.source))
    fun currentReviewSource(): EditorAiReviewSource? {
        val current = session.state
        return EditorAiExportReview.from(jobs.latest.value, current.project.id, current.revision).source
    }
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
                OutlinedButton(onClick = { gate.cancel(); finishLoaded(ReviewHistoryStatus.DISCARDED); onCreateVideo() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Criar novo vídeo / abrir pacote") }
                Text("Editar projeto atual: cole um plano visual. Não permite excluir mídias, mudar áudio ou duração. A alteração pode ser desfeita.")
                incomingProposal?.let { proposal ->
                    val matches = proposal.source == report.source
                    Text(if (matches) "Proposta recebida pelo Bridge • aguarda aprovação local" else "Proposta desatualizada.",
                        style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = {
                        val current = session.state
                        if (draft.isNotBlank() || approval != null) {
                            message = "Limpe ou conclua o plano atual primeiro."
                        } else {
                            val received = jobs.takeReviewProposal(proposal.id, current.project.id, current.revision)
                            if (received == null) message = "Proposta expirada ou desatualizada. Solicite nova exportação."
                            else {
                                loadedProposal = received
                                draft = received.planJson
                                gate.prepare(draft, current, currentReviewSource()).onSuccess {
                                    approval = it; message = "Proposta carregada. Confira e aprove localmente."
                                }.onFailure {
                                    finishLoaded(ReviewHistoryStatus.REJECTED)
                                    message = "Proposta rejeitada. Confira o projeto e a exportação."
                                }
                            }
                        }
                    }, enabled = !busy && matches && prepared == null && draft.isBlank(),
                        modifier = Modifier.fillMaxWidth()) { Text("Carregar proposta do GPT") }
                    TextButton(enabled = !busy, onClick = {
                        if (jobs.dismissReviewProposal(proposal.id)) message = "Proposta descartada. Nenhuma alteração realizada."
                    }) { Text("Descartar proposta recebida") }
                }
                OutlinedTextField(value = draft, onValueChange = {
                    if (it.length <= EditorAiApprovalGate.MAX_BYTES && it.toByteArray(Charsets.UTF_8).size <= EditorAiApprovalGate.MAX_BYTES) {
                        if (loadedProposal != null && it != loadedProposal?.planJson) finishLoaded(ReviewHistoryStatus.DISCARDED)
                        draft = it; gate.cancel(); approval = null; message = ""
                    } else message = "Plano acima de 8 KiB."
                }, label = { Text("Plano JSON do GPT") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, enabled = !busy && prepared == null)
                if (prepared == null && draft.isNotBlank()) {
                    TextButton(enabled = !busy, onClick = { gate.cancel(); finishLoaded(ReviewHistoryStatus.DISCARDED); draft = ""; message = "" }) { Text("Limpar plano") }
                }
                if (prepared != null) {
                    Text("Plano validado • ${prepared.operationCount} operações", style = MaterialTheme.typography.titleSmall)
                    Text(prepared.summary, style = MaterialTheme.typography.bodySmall)
                    prepared.reviewFindings?.let {
                        Text("Análise externa informada (não verificada pelo aplicativo)", style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        Text("Vinculada ao MP4 da revisão ${prepared.reviewSource?.revision}. Confira o conteúdo antes de aprovar.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (stale) "A revisão ou exportação mudou. Valide novamente." else "Confira o JSON acima antes de aprovar. A validação não alterou o projeto.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { gate.cancel(); approval = null; message = "" }) { Text("Revisar plano") }
                } else Button(onClick = {
                    gate.prepare(draft, session.state, currentReviewSource()).onSuccess { approval = it; message = "" }
                        .onFailure { message = "Plano rejeitado: ${it.message?.take(180) ?: "confira o JSON"}" }
                }, enabled = !busy && draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Validar plano") }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                Text("Revisão do MP4 exportado", style = MaterialTheme.typography.titleSmall)
                Text(report.title, style = MaterialTheme.typography.bodyMedium)
                Text(report.detail, style = MaterialTheme.typography.bodySmall)
                report.request?.let { request ->
                    OutlinedButton(onClick = {
                        val current = session.state
                        val fresh = EditorAiExportReview.from(jobs.latest.value, current.project.id, current.revision)
                        if (fresh.source == report.source && fresh.request != null) {
                            onCopyContext(EditorAiApprovalGate.context(current) + "\n\n" + fresh.request)
                            message = "Pedido e contexto copiados. O GPT pode enviar a proposta pelo Bridge; colar o JSON também é possível."
                        } else message = "Exportação mudou. Copie o pedido atualizado."
                    },
                        enabled = !busy && BridgePanelUiPolicy.agentReady(bridgeState), modifier = Modifier.fillMaxWidth()) { Text("Copiar pedido de revisão visual") }
                }
                Text("Histórico local de propostas", style = MaterialTheme.typography.titleSmall)
                if (!historyView.storageOk) Text("Histórico não pôde ser salvo. Os registros atuais podem desaparecer ao reiniciar.", style = MaterialTheme.typography.bodySmall)
                val recent = historyView.entries.filter { it.source.projectId == state.project.id }.take(8)
                if (recent.isEmpty()) Text("Nenhuma proposta registrada neste projeto.", style = MaterialTheme.typography.bodySmall)
                recent.forEach { entry ->
                    val label = when (entry.status) {
                        ReviewHistoryStatus.RECEIVED -> "Recebida"
                        ReviewHistoryStatus.LOADED -> "Carregada • não aplicada"
                        ReviewHistoryStatus.APPLIED_IN_EDITOR -> "Aplicada no editor • revisão ${entry.resultRevision}"
                        ReviewHistoryStatus.DISCARDED -> "Descartada"
                        ReviewHistoryStatus.REJECTED -> "Rejeitada"
                        ReviewHistoryStatus.SUPERSEDED -> "Substituída/expirada"
                        ReviewHistoryStatus.INTERRUPTED -> "Interrompida • não reaplicada"
                    }
                    Text("Revisão ${entry.source.revision} • $label • MP4 ${entry.source.sha256.take(10)}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Histórico informativo; não comprova salvamento do projeto, aprovação visual ou validade atual do MP4.", style = MaterialTheme.typography.bodySmall)
                Text("Após aplicar, volte à timeline para conferir a prévia. O GPT externo analisa os quadros pelo Bridge; esta tela não executa uma IA local.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = prepared != null && !stale && !busy, onClick = {
                val accepted = approval ?: return@TextButton
                approval = null
                gate.apply(accepted, draft, session, currentReviewSource()).onSuccess {
                    finishLoaded(ReviewHistoryStatus.APPLIED_IN_EDITOR, it)
                    onApplied()
                    message = "Aplicado na revisão $it. Confira a prévia; Desfazer continua disponível." +
                        if (accepted.reviewSource != null) " Exporte novamente para avaliar a correção; a análise anterior não valida esta revisão." else ""
                    draft = ""
                }.onFailure {
                    finishLoaded(ReviewHistoryStatus.REJECTED)
                    message = it.message?.take(180) ?: "Não foi possível aplicar."
                }
            }) { Text("Aprovar e aplicar") }
        },
        dismissButton = { TextButton(onClick = { gate.cancel(); onDismiss() }) { Text("Fechar") } },
    )
}
