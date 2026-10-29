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
import com.localcut.editor.termux.TermuxEngineUiState

@Composable
internal fun TermuxEngineRecoveryCard(
    engineState: TermuxEngineUiState,
    modifier: Modifier = Modifier,
) {
    val unavailable = engineState.errorCode == "TERMUX_ENGINE_UNAVAILABLE"
    if (engineState.paired && !unavailable) return

    val context = LocalContext.current
    val termuxInstalled = AndroidTermuxApp.isInstalled(context)
    val quickCommand = TermuxEngineRecoveryPolicy.quickCommand(engineState.paired)
    val repairCommand = TermuxEngineRecoveryPolicy.repairCommand(engineState.paired)

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
            if (termuxInstalled) {
                if (engineState.paired) {
                    "Termux detectado. Inicie o engine e volte ao LocalCut. Se não funcionar, use Configurar/Reparar."
                } else {
                    "Termux detectado. Inicie o pareamento, copie o código mostrado no Termux e informe acima."
                }
            } else {
                "Termux não foi detectado neste aparelho. Instale o Termux antes de configurar o Agent Engine."
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

        OutlinedButton(
            onClick = {
                AndroidTermuxApp.copyCommand(context, "LocalCut Agent Engine", quickCommand)
                Toast.makeText(context, "Comando copiado", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (engineState.paired) "Copiar comando para iniciar" else "Copiar comando de pareamento")
        }

        TextButton(
            onClick = {
                AndroidTermuxApp.copyCommand(context, "LocalCut Agent Engine - configurar", repairCommand)
                Toast.makeText(context, "Comando de configuração copiado", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Copiar Configurar/Reparar")
        }

        Text(
            "Cole o comando no Termux. O LocalCut não executa shell externo automaticamente e não copia tokens por segurança.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
