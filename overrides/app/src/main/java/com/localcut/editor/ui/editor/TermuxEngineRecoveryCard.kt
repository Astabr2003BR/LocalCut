package com.localcut.editor.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localcut.editor.termux.AndroidTermuxApp
import com.localcut.editor.termux.TermuxEngineRecoveryPolicy
import com.localcut.editor.termux.TermuxEngineUiPhase
import com.localcut.editor.termux.TermuxEngineUiState

@Composable
internal fun TermuxEngineRecoveryCard(
    engineState: TermuxEngineUiState,
    modifier: Modifier = Modifier,
) {
    val connected = engineState.phase == TermuxEngineUiPhase.CONNECTED
    val offerRepair = TermuxEngineRecoveryPolicy.shouldOfferRepair(
        paired = engineState.paired,
        connected = connected,
        errorCode = engineState.errorCode,
        capabilities = engineState.capabilities,
    )
    if (!offerRepair) return

    val context = LocalContext.current
    val termuxInstalled = AndroidTermuxApp.isInstalled(context)
    val quickCommand = TermuxEngineRecoveryPolicy.quickCommand(engineState.paired)
    val repairCommand = TermuxEngineRecoveryPolicy.repairCommand(engineState.paired)
    val missingReverse = TermuxEngineRecoveryPolicy
        .missingReverseCapabilities(engineState.capabilities)
        .sorted()

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Recuperação do Agent Engine",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when {
                connected && missingReverse.isNotEmpty() -> {
                    "Engine conectado, mas faltam recursos para Reverse: ${missingReverse.joinToString()}. " +
                        "Use Atualizar/Reparar para atualizar o engine, garantir FFmpeg e reiniciar o supervisor antigo com segurança."
                }
                termuxInstalled && engineState.paired -> {
                    "Termux detectado. Inicie o engine e volte ao LocalCut. Se não funcionar, use Atualizar/Reparar."
                }
                termuxInstalled -> {
                    "Termux detectado. Inicie o pareamento, copie o código mostrado no Termux e informe acima."
                }
                else -> {
                    "Termux não foi detectado neste aparelho. Instale o Termux antes de configurar o Agent Engine."
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = { AndroidTermuxApp.launch(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = termuxInstalled,
        ) {
            Text("Abrir Termux")
        }

        if (!connected) {
            OutlinedButton(
                onClick = {
                    AndroidTermuxApp.copyCommand(context, "LocalCut Agent Engine", quickCommand)
                    Toast.makeText(context, "Comando copiado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (engineState.paired) "Copiar comando para iniciar" else "Copiar comando de pareamento")
            }
        }

        TextButton(
            onClick = {
                AndroidTermuxApp.copyCommand(context, "LocalCut Agent Engine - atualizar", repairCommand)
                Toast.makeText(context, "Comando de atualização copiado", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (connected) "Copiar Atualizar/Reparar Engine" else "Copiar Configurar/Reparar")
        }

        Text(
            "Cole o comando no Termux. O LocalCut não executa shell externo automaticamente e não copia tokens por segurança.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
