package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localcut.editor.bridge.agent.BridgeUiPhase
import com.localcut.editor.bridge.agent.BridgeUiState

@Composable
fun BridgePanel(
    state: BridgeUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAlwaysConnectedChange: (Boolean) -> Unit,
    onRelayEndpointSave: (String) -> Unit,
    onRevokePairing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var relayDraft by remember(state.relayBaseUrl) { mutableStateOf(state.relayBaseUrl) }
    var expanded by rememberSaveable { mutableStateOf(BridgePanelUiPolicy.defaultExpanded(state)) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ChatGPT Bridge", fontWeight = FontWeight.SemiBold)
                    Text(
                        BridgePanelUiPolicy.summary(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (BridgePanelUiPolicy.needsAttention(state)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Fechar" else "Detalhes")
                }
            }

            if (!expanded) {
                state.message?.takeIf { BridgePanelUiPolicy.needsAttention(state) }?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.phase == BridgeUiPhase.OFFLINE || state.phase == BridgeUiPhase.ERROR) {
                    Button(
                        onClick = onConnect,
                        enabled = state.relayBaseUrl.isNotBlank(),
                    ) { Text("Conectar") }
                } else {
                    OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = relayDraft,
                    onValueChange = { relayDraft = it },
                    modifier = Modifier.weight(1f),
                    enabled = !state.paired,
                    singleLine = true,
                    label = { Text("Relay HTTPS") },
                    placeholder = { Text("https://relay.exemplo.com") },
                )
                OutlinedButton(
                    onClick = { onRelayEndpointSave(relayDraft) },
                    enabled = !state.paired && relayDraft.isNotBlank(),
                ) {
                    Text("Salvar")
                }
            }

            if (state.paired) {
                Text(
                    "Revogue o pareamento antes de trocar o servidor Relay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.pairingCode?.let { code ->
                Text(
                    "Código de pareamento: $code",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            state.exportProgressPercent?.let { progress ->
                Text("Exportação remota: $progress%", style = MaterialTheme.typography.bodySmall)
            }

            state.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sempre conectado", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Tenta restaurar a conexão após reiniciar o app ou o celular.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.alwaysConnected,
                    onCheckedChange = onAlwaysConnectedChange,
                )
            }

            if (state.paired) {
                OutlinedButton(onClick = onRevokePairing) {
                    Text("Revogar pareamento")
                }
            }
        }
    }
}
