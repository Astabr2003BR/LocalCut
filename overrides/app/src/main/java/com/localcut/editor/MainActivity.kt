package com.localcut.editor

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.data.assets.AndroidAssetImporter
import com.localcut.editor.data.assets.ImportedClipFactory
import com.localcut.editor.data.assets.VisualOverlayImportPolicy
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.media.AndroidExportCapabilitiesProvider
import com.localcut.editor.media.ExportEvent
import com.localcut.editor.media.ExportProfile
import com.localcut.editor.media.FinalPreviewRenderer
import com.localcut.editor.media.Media3ExportController
import com.localcut.editor.media.Media3PreviewController
import com.localcut.editor.media.SafExportDestination
import com.localcut.editor.session.EditorAction
import com.localcut.editor.ui.editor.EditorScreen
import com.localcut.editor.ui.editor.EditorViewModel
import com.localcut.editor.ui.editor.ExportUiState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        val importer = AndroidAssetImporter(contentResolver)
        val exportCapabilitiesProvider = AndroidExportCapabilitiesProvider(this)
        val exportCapabilities = exportCapabilitiesProvider.current()

        val visualImportLauncher = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
        ) { uris ->
            if (uris.isNotEmpty()) importUris(uris, importer)
        }

        val overlayImportLauncher = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
        ) { uris ->
            if (uris.isNotEmpty()) importOverlayUris(uris, importer)
        }

        val audioImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importUris(listOf(uri), importer)
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

        setContent {
            val exportState by exportUiState.collectAsState()
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9B8CFF),
                    onPrimary = Color(0xFF15131E),
                    secondaryContainer = Color(0xFF34323F),
                    onSecondaryContainer = Color(0xFFF8F7FF),
                    background = Color(0xFF0D0D11),
                    onBackground = Color(0xFFF8F8FA),
                    surface = Color(0xFF17171D),
                    onSurface = Color(0xFFF8F8FA),
                    surfaceVariant = Color(0xFF26262F),
                    onSurfaceVariant = Color(0xFFD6D6DE),
                    outline = Color(0xFF777783),
                    error = Color(0xFFFF7474),
                    errorContainer = Color(0xFF5A1F25),
                    onErrorContainer = Color(0xFFFFDADD),
                )
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

    private fun importUris(uris: List<Uri>, importer: AndroidAssetImporter) {
        activityScope.launch {
            var importedCount = 0
            var failedCount = 0

            for (uri in uris) {
                val importResult = withContext(Dispatchers.IO) { importer.import(uri) }
                if (importResult.isFailure) {
                    failedCount += 1
                    val error = importResult.exceptionOrNull()
                    showError(error?.message ?: "Não foi possível importar a mídia")
                    continue
                }
                val asset = importResult.getOrThrow()

                val project = editorViewModel.sessionState.value.project
                val targetType = if (asset.type == AssetType.AUDIO) TrackType.AUDIO else TrackType.VISUAL
                val track = project.tracks.firstOrNull { it.type == targetType }
                if (track == null) {
                    failedCount += 1
                    showError("Trilha ${targetType.name.lowercase()} não encontrada")
                    continue
                }

                val startUs = track.clips.maxOfOrNull { it.startUs + it.durationUs } ?: 0L
                val clipResult = ImportedClipFactory.create(
                    asset = asset,
                    clipId = UUID.randomUUID().toString(),
                    startUs = startUs,
                )
                clipResult.onSuccess { clip ->
                    editorViewModel.dispatch(EditorAction.AddImportedClip(track.id, asset, clip))
                    importedCount += 1
                }.onFailure { error ->
                    failedCount += 1
                    showError(error.message ?: "Não foi possível criar o clipe")
                }
            }

            if (importedCount > 0) {
                val message = if (importedCount == 1) "1 mídia importada" else "$importedCount mídias importadas"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            } else if (failedCount == 0) {
                showError("Nenhuma mídia selecionada")
            }
        }
    }

    private fun importOverlayUris(uris: List<Uri>, importer: AndroidAssetImporter) {
        activityScope.launch {
            var importedCount = 0
            var failedCount = 0

            for (uri in uris) {
                val importResult = withContext(Dispatchers.IO) { importer.import(uri) }
                if (importResult.isFailure) {
                    failedCount += 1
                    showError(importResult.exceptionOrNull()?.message ?: "Não foi possível importar o overlay")
                    continue
                }
                val asset = importResult.getOrThrow()
                val project = editorViewModel.sessionState.value.project
                val clipResult = VisualOverlayImportPolicy.create(
                    project = project,
                    asset = asset,
                    clipId = UUID.randomUUID().toString(),
                    playheadUs = editorViewModel.previewState.value.positionUs,
                )
                clipResult.onSuccess { clip ->
                    editorViewModel.dispatch(EditorAction.AddImportedOverlayClip(asset, clip))
                    if (editorViewModel.sessionState.value.project.findClip(clip.id) != null) {
                        importedCount += 1
                    } else {
                        failedCount += 1
                        showError(editorViewModel.sessionState.value.lastError ?: "Não foi possível inserir o overlay")
                    }
                }.onFailure { error ->
                    failedCount += 1
                    showError(error.message ?: "Não foi possível criar o overlay")
                }
            }

            if (importedCount > 0) {
                val message = if (importedCount == 1) "1 overlay importado" else "$importedCount overlays importados"
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            } else if (failedCount == 0) {
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
                    exportUiState.value = ExportUiState(message = loaded.exceptionOrNull()?.message ?: "Falha ao abrir prévia final")
                }
            }.onFailure { error ->
                exportUiState.value = ExportUiState(message = error.message ?: "Falha na prévia final")
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
