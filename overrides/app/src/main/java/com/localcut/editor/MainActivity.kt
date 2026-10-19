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
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.data.assets.ImportedClipFactory
import com.localcut.editor.data.project.AtomicProjectStore
import com.localcut.editor.data.project.ProjectStoreErrorCode
import com.localcut.editor.data.project.ProjectStoreException
import com.localcut.editor.domain.model.AssetType
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.TrackType
import com.localcut.editor.domain.timeline.TimelineMath
import com.localcut.editor.media.ExportEvent
import com.localcut.editor.media.FinalPreviewRenderer
import com.localcut.editor.media.Media3ExportController
import com.localcut.editor.media.Media3PreviewController
import com.localcut.editor.media.ProjectCompositionCompiler
import com.localcut.editor.media.RenderPlanErrorCode
import com.localcut.editor.media.RenderPlanException
import com.localcut.editor.media.SafExportDestination
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.EditorAction
import com.localcut.editor.session.ExecutorDelayScheduler
import com.localcut.editor.session.PreviewErrorCode
import com.localcut.editor.session.PreviewLoadException
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
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
    private lateinit var editorViewModel: EditorViewModel
    private lateinit var previewController: Media3PreviewController
    private lateinit var exportController: Media3ExportController
    private lateinit var finalPreviewRenderer: FinalPreviewRenderer
    private lateinit var autosave: DebouncedProjectSaver
    private lateinit var autosaveScheduler: ExecutorDelayScheduler
    private val exportUiState = MutableStateFlow(ExportUiState())
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastFinalPreviewFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val primaryStore = AtomicProjectStore(filesDir.toPath().resolve("projects/default"))
        val loadResult = primaryStore.load()
        val loadError = loadResult.exceptionOrNull() as? ProjectStoreException
        val activeStore = if (loadResult.isSuccess || loadError?.code == ProjectStoreErrorCode.NOT_FOUND) {
            primaryStore
        } else {
            Toast.makeText(this, "Projeto anterior preservado; abrindo recuperação.", Toast.LENGTH_LONG).show()
            AtomicProjectStore(filesDir.toPath().resolve("projects/recovery-${System.currentTimeMillis()}"))
        }
        val initialProject = loadResult.getOrNull() ?: Project.create("Meu projeto", 1080, 1920, 30)

        val importer = AndroidAssetImporter(contentResolver)
        val compositionCompiler = ProjectCompositionCompiler(AssetUriResolver(contentResolver))
        previewController = Media3PreviewController(this, compositionCompiler)
        exportController = Media3ExportController(this, compositionCompiler, cacheDir)
        finalPreviewRenderer = FinalPreviewRenderer(exportController, File(cacheDir, "final-previews"))
        autosaveScheduler = ExecutorDelayScheduler()
        autosave = DebouncedProjectSaver(activeStore, autosaveScheduler, 450L)

        val previewLoader = object : ProjectPreviewLoader {
            override fun load(project: Project): Result<Unit> = previewController.load(project).recoverCatching { error ->
                if (error is RenderPlanException && error.code == RenderPlanErrorCode.ASSET_UNAVAILABLE) {
                    throw PreviewLoadException(PreviewErrorCode.ASSET_UNAVAILABLE, error.assetId, error.message ?: "asset unavailable", error)
                }
                throw PreviewLoadException(PreviewErrorCode.PREVIEW_FAILED, null, error.message ?: "preview failed", error)
            }
        }
        val session = ProjectSession(initialProject, previewLoader, autosave, activeStore)
        editorViewModel = EditorViewModel(session, previewController)
        previewController.load(initialProject)

        val visualImportLauncher = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
        ) { uris ->
            if (uris.isNotEmpty()) importUris(uris, importer)
        }

        val audioImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importUris(listOf(uri), importer)
        }

        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            if (uri != null) startExport(uri)
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
                    onImportRequested = {
                        visualImportLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onImportAudioRequested = { audioImportLauncher.launch(arrayOf("audio/*")) },
                    onExportRequested = {
                        if (!exportUiState.value.running) {
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

    private fun startExport(destinationUri: Uri) {
        if (exportUiState.value.running) return
        val project = editorViewModel.sessionState.value.project
        if (TimelineMath.durationUs(project) <= 0L) {
            showError("Adicione uma mídia antes de exportar.")
            return
        }
        exportUiState.value = ExportUiState(running = true, progressPercent = 0, message = "Exportando…")
        activityScope.launch {
            try {
                exportController.export(project, SafExportDestination(contentResolver, destinationUri)).collect(::onExportEvent)
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
        if (::exportController.isInitialized) exportController.cancel()
        activityScope.cancel()
        lastFinalPreviewFile?.delete()
        if (::autosave.isInitialized) autosave.flushNow()
        if (::editorViewModel.isInitialized) editorViewModel.close()
        if (::autosaveScheduler.isInitialized) autosaveScheduler.close()
        super.onDestroy()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
