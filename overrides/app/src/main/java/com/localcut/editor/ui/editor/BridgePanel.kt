package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onRevokePairing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("ChatGPT Bridge", fontWeight = FontWeight.SemiBold)
                    Text(
                        phaseLabel(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.phase == BridgeUiPhase.OFFLINE || state.phase == BridgeUiPhase.ERROR) {
                    Button(onClick = onConnect) { Text("Conectar") }
                } else {
                    OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
                }
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

private fun phaseLabel(state: BridgeUiState): String = when (state.phase) {
    BridgeUiPhase.OFFLINE -> if (state.paired) "Offline — pareado" else "Offline"
    BridgeUiPhase.PAIRING -> "Pareando"
    BridgeUiPhase.CONNECTING -> "Conectando"
    BridgeUiPhase.ONLINE -> "Online"
    BridgeUiPhase.EDITING -> "Editando projeto"
    BridgeUiPhase.EXPORTING -> "Exportando"
    BridgeUiPhase.ERROR -> "Erro de conexão"
}
