package com.localcut.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.media.AndroidExportCapabilitiesProvider
import com.localcut.editor.media.ExportEvent
import com.localcut.editor.media.ExportProfile
import com.localcut.editor.media.FinalPreviewRenderer
import com.localcut.editor.media.Media3ExportController
import com.localcut.editor.media.Media3PreviewController
import com.localcut.editor.media.PreviewResumePolicy
import com.localcut.editor.media.SafExportDestination
import com.localcut.editor.mobile.SharedMediaIntentParser
import com.localcut.editor.ui.editor.BridgePanel
import com.localcut.editor.ui.editor.EditorScreen
import com.localcut.editor.ui.editor.EditorToolbarLayoutPolicy
import com.localcut.editor.ui.editor.EditorViewModel
import com.localcut.editor.ui.editor.ExportUiState
import com.localcut.editor.ui.editor.FinalPreviewUiPolicy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@UnstableApi
@OptIn(ExperimentalApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var runtime: LocalCutProcessRuntime
    private lateinit var editorViewModel: EditorViewModel
    private lateinit var previewController: Media3PreviewController
    private lateinit var exportController: Media3ExportController
    private lateinit var finalPreviewRenderer: FinalPreviewRenderer
    private val exportUiState = MutableStateFlow(ExportUiState())
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastFinalPreviewFile: File? = null
    private var pendingExportProfile: ExportProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runtime = LocalCutProcessRuntime.get(this)
        runtime.loadWarning?.let { warning ->
            Toast.makeText(this, warning, Toast.LENGTH_LONG).show()
        }
        previewController = runtime.previewController
        exportController = runtime.exportController
        finalPreviewRenderer = runtime.finalPreviewRenderer
        editorViewModel = runtime.editorViewModel

        val exportCapabilitiesProvider = AndroidExportCapabilitiesProvider(this)
        val exportCapabilities = exportCapabilitiesProvider.current()

        val visualImportLauncher = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
        ) { uris ->
            if (uris.isNotEmpty()) importUris(uris)
        }

        val overlayImportLauncher = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
        ) { uris ->
            if (uris.isNotEmpty()) importOverlayUris(uris)
        }

        val audioImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importUris(listOf(uri))
        }

        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            val profile = pendingExportProfile
            pendingExportProfile = null
            if (uri != null) {
                if (profile == null) {
                    showError("Configuração de exportação expirada. Tente novamente.")
                } else {
                    startExport(uri, profile)
                }
            }
        }

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                runtime.bridgeControl.connect()
            } else {
                showError("Permita notificações para manter o ChatGPT Bridge ativo em segundo plano.")
            }
        }
        val connectBridge = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                runtime.bridgeControl.connect()
            }
        }

        setContent {
            val exportState by exportUiState.collectAsState()
            val bridgeState by runtime.bridgeControl.state.collectAsState()
            val editorWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
            val editorMinimumInteractiveSize = EditorToolbarLayoutPolicy
                .minimumInteractiveSizeDp(editorWidthDp)
                .dp

            LaunchedEffect(exportState.running, exportState.message) {
                val completedMessage = exportState.message
                if (!exportState.running && completedMessage != null) {
                    delay(6_000L)
                    val current = exportUiState.value
                    if (!current.running && current.message == completedMessage) {
                        exportUiState.value = ExportUiState()
                    }
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF20D5D2),
                    onPrimary = Color(0xFF001817),
                    secondaryContainer = Color(0xFF222226),
                    onSecondaryContainer = Color(0xFFF5F5F7),
                    background = Color(0xFF08080A),
                    onBackground = Color(0xFFF5F5F7),
                    surface = Color(0xFF111114),
                    onSurface = Color(0xFFF5F5F7),
                    surfaceVariant = Color(0xFF1B1B1F),
                    onSurfaceVariant = Color(0xFFC4C4CA),
                    outline = Color(0xFF4A4A50),
                    error = Color(0xFFFF7474),
                    errorContainer = Color(0xFF5A1F25),
                    onErrorContainer = Color(0xFFFFDADD),
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    BridgePanel(
                        state = bridgeState,
                        onConnect = connectBridge,
                        onDisconnect = runtime.bridgeControl::disconnect,
                        onAlwaysConnectedChange = runtime.bridgeControl::setAlwaysConnected,
                        onRelayEndpointSave = runtime.bridgeControl::setRelayBaseUrl,
                        onRevokePairing = runtime.bridgeControl::revokePairing,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides editorMinimumInteractiveSize,
                        ) {
                            EditorScreen(
                                viewModel = editorViewModel,
                                previewController = previewController,
                                exportState = exportState,
                                exportCapabilities = exportCapabilities,
                                onImportRequested = {
                                    visualImportLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onImportAudioRequested = { audioImportLauncher.launch(arrayOf("audio/*")) },
                                onImportOverlayRequested = {
                                    overlayImportLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onExportRequested = { profile ->
                                    if (!exportUiState.value.running) {
                                        pendingExportProfile = profile
                                        exportLauncher.launch("LocalCut-${System.currentTimeMillis()}.mp4")
                                    }
                                },
                                onCancelExport = { exportController.cancel() },
                                onFinalPreviewRequested = ::startFinalPreview,
                            )
                        }
                    }
                }
            }
        }

        consumeSharedMediaIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!::editorViewModel.isInitialized || !::previewController.isInitialized) return
        val preview = editorViewModel.previewState.value
        if (PreviewResumePolicy.shouldRefresh(preview.durationUs, preview.playing)) {
            previewController.seekToUs(preview.positionUs)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSharedMediaIntent(intent)
    }

    private fun importUris(uris: List<Uri>, shared: Boolean = false) {
        activityScope.launch {
            val result = if (shared) {
                runtime.mediaIngestCoordinator.importSharedUris(uris)
            } else {
                runtime.mediaIngestCoordinator.importUris(uris)
            }
            result.errors.forEach(::showError)

            if (result.importedCount > 0) {
                val message = if (result.importedCount == 1) {
                    "1 mídia importada"
                } else {
                    "${result.importedCount} mídias importadas"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            } else if (result.failedCount == 0) {
                showError("Nenhuma mídia selecionada")
            }
        }
    }

    private fun consumeSharedMediaIntent(sourceIntent: Intent?) {
        if (sourceIntent == null) return
        val action = sourceIntent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val parsed = SharedMediaIntentParser.parse(
            action = action,
            mimeType = sourceIntent.type,
            uriStrings = sharedUriStrings(sourceIntent),
        )
        markSharedIntentConsumed(sourceIntent)

        parsed.onSuccess { request ->
            importUris(request.uriStrings.map(Uri::parse), shared = true)
        }.onFailure { error ->
            showError(error.message ?: "Não foi possível importar a mídia compartilhada")
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUriStrings(sourceIntent: Intent): List<String?> = when (sourceIntent.action) {
        Intent.ACTION_SEND -> listOf(
            sourceIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString(),
        )
        Intent.ACTION_SEND_MULTIPLE -> sourceIntent
            .getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?.map { it.toString() }
            .orEmpty()
        else -> emptyList()
    }

    private fun markSharedIntentConsumed(sourceIntent: Intent) {
        setIntent(
            Intent(sourceIntent).apply {
                action = Intent.ACTION_MAIN
                type = null
                removeExtra(Intent.EXTRA_STREAM)
                clipData = null
            },
        )
    }

    private fun importOverlayUris(uris: List<Uri>) {
        activityScope.launch {
            val result = runtime.mediaIngestCoordinator.importOverlayUris(
                uris = uris,
                playheadUs = editorViewModel.previewState.value.positionUs,
            )
            result.errors.forEach(::showError)

            if (result.importedCount > 0) {
                val message = if (result.importedCount == 1) {
                    "1 overlay importado"
                } else {
                    "${result.importedCount} overlays importados"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            } else if (result.failedCount == 0) {
                showError("Nenhum overlay selecionado")
            }
        }
    }

    private fun startExport(destinationUri: Uri, profile: ExportProfile) {
        if (exportUiState.value.running) return
        val project = editorViewModel.sessionState.value.project
        if (TimelineMath.durationUs(project) <= 0L) {
            showError("Adicione uma mídia antes de exportar.")
            return
        }
        exportUiState.value = ExportUiState(running = true, progressPercent = 0, message = "Exportando…")
        activityScope.launch {
            try {
                exportController.export(
                    project,
                    SafExportDestination(contentResolver, destinationUri),
                    profile,
                ).collect(::onExportEvent)
            } catch (error: Throwable) {
                exportUiState.value = ExportUiState(message = error.message ?: "Falha ao exportar")
            }
        }
    }

    private fun startFinalPreview() {
        if (exportUiState.value.running) return
        val project = editorViewModel.sessionState.value.project
        val durationUs = TimelineMath.durationUs(project)
        if (durationUs <= 0L) {
            showError("Adicione uma mídia antes da prévia final.")
            return
        }
        exportUiState.value = ExportUiState(running = true, message = "Renderizando prévia final…")
        activityScope.launch {
            val result = finalPreviewRenderer.renderRange(project, 0L, durationUs)
            result.onSuccess { file ->
                val loaded = previewController.loadRenderedFile(file, durationUs, project.fps)
                if (loaded.isSuccess) {
                    lastFinalPreviewFile?.delete()
                    lastFinalPreviewFile = file
                    exportUiState.value = ExportUiState(message = "Prévia final pronta")
                } else {
                    file.delete()
                    val error = loaded.exceptionOrNull() ?: IllegalStateException("preview load failed")
                    exportUiState.value = ExportUiState(message = FinalPreviewUiPolicy.failureMessage(error))
                }
            }.onFailure { error ->
                exportUiState.value = ExportUiState(message = FinalPreviewUiPolicy.failureMessage(error))
            }
        }
    }

    private fun onExportEvent(event: ExportEvent) {
        exportUiState.value = when (event) {
            ExportEvent.Started -> ExportUiState(running = true, progressPercent = 0, message = "Exportando…")
            is ExportEvent.Progress -> ExportUiState(running = true, progressPercent = event.percent, message = "Exportando…")
            ExportEvent.Completed -> ExportUiState(progressPercent = 100, message = "MP4 exportado")
            ExportEvent.Cancelled -> ExportUiState(message = "Exportação cancelada")
            is ExportEvent.Failed -> ExportUiState(message = event.message)
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        lastFinalPreviewFile?.delete()
        if (::runtime.isInitialized) runtime.flushAutosave()
        super.onDestroy()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
