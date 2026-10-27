package com.localcut.editor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.termux.TermuxEngineAutoConnectPolicy
import com.localcut.editor.termux.TermuxEngineUiPhase
import com.localcut.editor.termux.TermuxEngineUiSession
import com.localcut.editor.termux.TermuxEngineUiState
import kotlinx.coroutines.launch

@Composable
fun TermuxEnginePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val runtime = remember(context) { TermuxAgentEngineRuntime.get(context) }
    val session = remember(runtime) {
        TermuxEngineUiSession(
            pairAction = { code -> runtime.pair(code).map { Unit } },
            pingAction = { runtime.client.ping().map { Unit } },
            statusAction = runtime.client::getStatus,
            capabilitiesAction = runtime.client::getCapabilities,
            clearToken = runtime.tokenStore::clear,
        )
    }
    var state by remember(runtime) {
        mutableStateOf(TermuxEngineUiState.initial(paired = runtime.tokenStore.load() != null))
    }
    var pairingDraft by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(runtime) {
        if (TermuxEngineAutoConnectPolicy.shouldProbe(state)) {
            val checking = state.pairing()
            state = checking
            state = session.check(checking)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Termux Agent Engine",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFD7D7DC),
        )
        Text(
            state.statusText,
            style = MaterialTheme.typography.bodySmall,
            color = when (state.phase) {
                TermuxEngineUiPhase.CONNECTED -> Color(0xFF20D5D2)
                TermuxEngineUiPhase.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        if (!state.paired) {
            OutlinedTextField(
                value = pairingDraft,
                onValueChange = { pairingDraft = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                singleLine = true,
                label = { Text("Código do Termux") },
                placeholder = { Text("ABCD-EFGH-JKLM") },
            )
            Text(
                "No Termux: python termux-engine/localcut_supervisor.py --pair",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    val starting = state.pairing()
                    state = starting
                    scope.launch {
                        state = if (starting.paired) {
                            session.check(starting)
                        } else {
                            session.pair(pairingDraft, starting)
                        }
                        if (state.paired) pairingDraft = ""
                    }
                },
                enabled = !state.busy && (state.paired || pairingDraft.isNotBlank()),
            ) {
                Text(state.actionLabel)
            }
        }

        state.engineVersion?.let { version ->
            Text(
                "Engine $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.capabilities.isNotEmpty()) {
            Text(
                "Recursos: ${state.capabilities.sorted().joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.errorCode?.let { code ->
            Text(
                "Código: $code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        TermuxMediaAnalyzePanel(engineState = state)
        TermuxApprovalPanel(engineState = state)
    }
}
