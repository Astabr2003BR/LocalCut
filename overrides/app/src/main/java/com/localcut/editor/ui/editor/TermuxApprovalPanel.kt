package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localcut.editor.termux.TermuxAgentApproval
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.termux.TermuxApprovalDecision
import com.localcut.editor.termux.TermuxEngineUiPhase
import com.localcut.editor.termux.TermuxEngineUiState
import kotlinx.coroutines.launch

@Composable
fun TermuxApprovalPanel(
    engineState: TermuxEngineUiState,
    modifier: Modifier = Modifier,
) {
    if (engineState.phase != TermuxEngineUiPhase.CONNECTED) return

    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { TermuxAgentEngineRuntime.get(context) }
    val scope = rememberCoroutineScope()
    var approvals by remember { mutableStateOf<List<TermuxAgentApproval>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            runtime.approvalClient.getPendingApprovals().fold(
                onSuccess = {
                    approvals = it
                    loaded = true
                    message = null
                },
                onFailure = {
                    message = "Aprovações indisponíveis • ${it.message ?: "TERMUX_AGENT_UNAVAILABLE"}"
                },
            )
            busy = false
        }
    }

    fun resolve(approval: TermuxAgentApproval, decision: TermuxApprovalDecision) {
        if (busy) return
        busy = true
        scope.launch {
            runtime.approvalClient.resolveApproval(approval.id, decision).fold(
                onSuccess = { resolved ->
                    message = if (resolved.status == "APPROVED") {
                        "Operação aprovada explicitamente."
                    } else {
                        "Operação negada."
                    }
                    runtime.approvalClient.getPendingApprovals().onSuccess { approvals = it }
                    loaded = true
                },
                onFailure = {
                    message = "Falha ao responder • ${it.message ?: "TERMUX_AGENT_APPROVAL_FAILED"}"
                },
            )
            busy = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Aprovações críticas",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(onClick = ::refresh, enabled = !busy) {
            Text(if (busy) "Verificando…" else "Verificar aprovações")
        }

        if (loaded && approvals.isEmpty()) {
            Text("Nenhuma operação crítica aguardando aprovação.", style = MaterialTheme.typography.bodySmall)
        }

        approvals.forEach { approval ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(approval.operation, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Aguardando decisão explícita. Sem resposta, não executa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { resolve(approval, TermuxApprovalDecision.APPROVE) },
                        enabled = !busy,
                    ) { Text("APROVAR") }
                    OutlinedButton(
                        onClick = { resolve(approval, TermuxApprovalDecision.DENY) },
                        enabled = !busy,
                    ) { Text("NEGAR") }
                }
            }
        }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Falha") || it.startsWith("Aprovações indisponíveis")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
