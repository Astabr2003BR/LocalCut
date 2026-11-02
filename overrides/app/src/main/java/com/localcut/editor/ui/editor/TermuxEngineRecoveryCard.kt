package com.localcut.editor.ui.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localcut.editor.termux.AndroidTermuxApp
import com.localcut.editor.termux.AndroidTermuxAutomation
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.termux.TermuxAutomationPolicy
import com.localcut.editor.termux.TermuxEngineRecoveryPolicy
import com.localcut.editor.termux.TermuxEngineUiPhase
import com.localcut.editor.termux.TermuxEngineUiState
import com.localcut.editor.termux.TermuxRunCommandSpec

@Composable
internal fun TermuxEngineRecoveryCard(
    engineState: TermuxEngineUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val connected = engineState.phase == TermuxEngineUiPhase.CONNECTED
    val termuxInstalled = AndroidTermuxApp.isInstalled(appContext)
    val missingCreative = TermuxEngineRecoveryPolicy
        .missingCreativeCapabilities(engineState.capabilities)
        .sorted()
    val fullManagedEngine = connected &&
        TermuxAutomationPolicy.hasManagedAutomationCapability(engineState.capabilities) &&
        TermuxAutomationPolicy.hasFullCreativeCapabilities(engineState.capabilities)
    var managedReady by remember(context) {
        mutableStateOf(AndroidTermuxAutomation.isManagedReady(appContext))
    }
    var automationPermissionGranted by remember(context, engineState.paired) {
        mutableStateOf(AndroidTermuxAutomation.hasRunCommandPermission(appContext))
    }

    LaunchedEffect(fullManagedEngine) {
        if (fullManagedEngine && !managedReady) {
            AndroidTermuxAutomation.markManagedReady(appContext)
            managedReady = true
        }
    }

    val offerRepair = TermuxEngineRecoveryPolicy.shouldOfferRepair(
        paired = engineState.paired,
        connected = connected,
        errorCode = engineState.errorCode,
        managedReady = managedReady,
        capabilities = engineState.capabilities,
    )
    val needsPermission = engineState.paired && termuxInstalled && !automationPermissionGranted
    if (!offerRepair && !needsPermission) return

    val bootstrapCommand = TermuxEngineRecoveryPolicy.bootstrapCommand(engineState.paired)
    val runtime = remember(appContext) { TermuxAgentEngineRuntime.get(appContext) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        automationPermissionGranted = granted
        if (granted) {
            runtime.wakeAutomation(force = true)
            Toast.makeText(context, "Automação do Agent Engine autorizada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissão de automação não concedida", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(
        engineState.paired,
        managedReady,
        connected,
        engineState.capabilities,
        automationPermissionGranted,
    ) {
        if (
            automationPermissionGranted &&
            TermuxAutomationPolicy.needsMaintenance(
                paired = engineState.paired,
                managedReady = managedReady,
                connected = connected,
                capabilities = engineState.capabilities,
            )
        ) {
            runtime.wakeAutomation()
        }
    }

    val needsInitialBootstrap = engineState.paired && !managedReady && (
        !connected ||
            !TermuxAutomationPolicy.hasManagedAutomationCapability(engineState.capabilities) ||
            missingCreative.isNotEmpty()
        )

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Automação do Agent Engine",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when {
                !termuxInstalled -> "Termux não foi detectado neste aparelho."
                needsPermission -> "Falta uma autorização única do Android para o LocalCut acordar o Termux automaticamente."
                needsInitialBootstrap ->
                    "Este engine ainda não tem a automação completa. Faça a preparação única abaixo; depois o LocalCut inicia, atualiza e repara o engine sozinho."
                managedReady && !connected -> "Engine offline. O LocalCut solicitou a recuperação automática."
                managedReady && missingCreative.isNotEmpty() ->
                    "Automação ativa. O watchdog vai reparar os recursos ausentes: ${missingCreative.joinToString()}."
                else -> "Automação pronta para recuperar o Agent Engine."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (needsPermission) {
            OutlinedButton(
                onClick = { permissionLauncher.launch(TermuxRunCommandSpec.PERMISSION) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ativar automação do Agent Engine")
            }
        }

        if (termuxInstalled && needsInitialBootstrap) {
            OutlinedButton(
                onClick = {
                    AndroidTermuxApp.copyCommand(context, "LocalCut - preparação única", bootstrapCommand)
                    AndroidTermuxApp.launch(context)
                    Toast.makeText(context, "Preparação única copiada; cole no Termux", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Preparar automação (uma vez)")
            }
        }

        if (!engineState.paired && termuxInstalled) {
            OutlinedButton(
                onClick = {
                    AndroidTermuxApp.copyCommand(context, "LocalCut - configuração inicial", bootstrapCommand)
                    AndroidTermuxApp.launch(context)
                    Toast.makeText(context, "Configuração inicial copiada; cole no Termux", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Configurar e parear pela primeira vez")
            }
        }

        if (managedReady && offerRepair) {
            TextButton(
                onClick = {
                    runtime.wakeAutomation(force = true)
                    Toast.makeText(context, "Recuperação automática solicitada", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = automationPermissionGranted,
            ) {
                Text("Executar recuperação agora")
            }
        }

        Text(
            if (managedReady) {
                "Depois da ativação, comandos manuais ficam apenas como recuperação de emergência."
            } else {
                "A preparação manual é necessária somente nesta migração porque o Termux bloqueia comandos externos até a autorização inicial."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
