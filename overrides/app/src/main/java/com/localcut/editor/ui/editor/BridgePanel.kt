package com.localcut.editor.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        color = Color(0xFF0D0D0F),
        shape = RoundedCornerShape(if (expanded) 8.dp else 5.dp),
    ) {
        if (!expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = bridgeDot(state),
                    color = bridgeColor(state),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = BridgePanelUiPolicy.summary(state),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (BridgePanelUiPolicy.needsAttention(state)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFFAFAFB5)
                    },
                    maxLines = 1,
                )
                Text(
                    text = "Agente ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF20D5D2),
                    maxLines = 1,
                )
            }
            return@Surface
        }

        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bridgeDot(state),
                    color = bridgeColor(state),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "ChatGPT • Agente ao vivo",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFD7D7DC),
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = false }) { Text("Fechar") }
            }

            Text(
                BridgePanelUiPolicy.summary(state),
                style = MaterialTheme.typography.bodySmall,
                color = if (BridgePanelUiPolicy.needsAttention(state)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (BridgePanelUiPolicy.agentReady(state)) {
                Text(
                    "ChatGPT pode ler o projeto, editar a timeline e ver a prévia enquanto o editor estiver aberto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF20D5D2),
                )
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
                    ) { Text("Conectar ChatGPT") }
                } else {
                    OutlinedButton(onClick = onDisconnect) { Text("Pausar agente") }
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
                ) { Text("Salvar") }
            }

            if (state.paired) {
                Text(
                    "Pareamento seguro ativo. Revogue antes de trocar o servidor Relay.",
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
                Text(
                    "Use este código uma vez no LocalCut Bridge do ChatGPT para vincular este aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("Manter agente conectado", style = MaterialTheme.typography.bodyMedium)
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
                OutlinedButton(onClick = onRevokePairing) { Text("Revogar pareamento") }
            }

            TermuxEnginePanel()
        }
    }
}

private fun bridgeDot(state: BridgeUiState): String = when (state.phase) {
    BridgeUiPhase.ERROR -> "●"
    BridgeUiPhase.ONLINE,
    BridgeUiPhase.EDITING,
    BridgeUiPhase.EXPORTING -> "●"
    BridgeUiPhase.PAIRING,
    BridgeUiPhase.CONNECTING -> "◉"
    BridgeUiPhase.OFFLINE -> "○"
}

private fun bridgeColor(state: BridgeUiState): Color = when (state.phase) {
    BridgeUiPhase.ERROR -> Color(0xFFFF6B6B)
    BridgeUiPhase.ONLINE,
    BridgeUiPhase.EDITING,
    BridgeUiPhase.EXPORTING -> Color(0xFF20D5D2)
    BridgeUiPhase.PAIRING,
    BridgeUiPhase.CONNECTING -> Color(0xFFFFC857)
    BridgeUiPhase.OFFLINE -> Color(0xFF77777D)
}
