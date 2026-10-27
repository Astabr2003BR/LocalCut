package com.localcut.editor

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import com.localcut.editor.bridge.agent.AndroidMediaStoreBridgeExportTargetFactory
import com.localcut.editor.bridge.agent.AsyncBridgeTermuxAgentTaskPort
import com.localcut.editor.bridge.agent.BridgeActiveProjectContext
import com.localcut.editor.bridge.agent.BridgeCommandExecutor
import com.localcut.editor.bridge.agent.BridgeJobRegistry
import com.localcut.editor.bridge.agent.BridgeMessageProcessor
import com.localcut.editor.bridge.agent.BridgePlaybackPort
import com.localcut.editor.bridge.agent.BridgeTermuxAgentTaskPort
import com.localcut.editor.bridge.agent.LocalCutBridgeControl
import com.localcut.editor.bridge.agent.Media3BridgeExportPort
import com.localcut.editor.bridge.security.AndroidKeystoreDeviceIdentityStore
import com.localcut.editor.bridge.transport.BridgeInboundMessageHandler
import com.localcut.editor.data.assets.AndroidAssetImporter
import com.localcut.editor.data.assets.AssetUriResolver
import com.localcut.editor.data.project.AtomicProjectStore
import com.localcut.editor.data.project.ProjectStoreErrorCode
import com.localcut.editor.data.project.ProjectStoreException
import com.localcut.editor.domain.model.Project
import com.localcut.editor.media.AndroidExportCapabilitiesProvider
import com.localcut.editor.media.FinalPreviewRenderer
import com.localcut.editor.media.Media3ExportController
import com.localcut.editor.media.Media3PreviewController
import com.localcut.editor.media.ProjectCompositionCompiler
import com.localcut.editor.media.RenderPlanErrorCode
import com.localcut.editor.media.RenderPlanException
import com.localcut.editor.mobile.MediaIngestCoordinator
import com.localcut.editor.mobile.SharedMediaStager
import com.localcut.editor.session.DebouncedProjectSaver
import com.localcut.editor.session.ExecutorDelayScheduler
import com.localcut.editor.session.PreviewErrorCode
import com.localcut.editor.session.PreviewLoadException
import com.localcut.editor.session.ProjectPreviewLoader
import com.localcut.editor.session.ProjectSession
import com.localcut.editor.session.SharedPreferencesProjectRevisionClock
import com.localcut.editor.termux.TermuxAgentEngineRuntime
import com.localcut.editor.ui.editor.EditorViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@UnstableApi
@OptIn(ExperimentalApi::class)
class LocalCutProcessRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val autosaveScheduler = ExecutorDelayScheduler()
    val autosave: DebouncedProjectSaver
    val previewController: Media3PreviewController
    val exportController: Media3ExportController
    val finalPreviewRenderer: FinalPreviewRenderer
    val session: ProjectSession
    val editorViewModel: EditorViewModel
    val mediaIngestCoordinator: MediaIngestCoordinator
    val bridgeJobs = BridgeJobRegistry()
    val bridgeCommandExecutor: BridgeCommandExecutor
    val bridgeTermuxAgentPort: BridgeTermuxAgentTaskPort
    val bridgeMessageProcessor: BridgeMessageProcessor
    val bridgeInboundMessageHandler: BridgeInboundMessageHandler
    val bridgeControl: LocalCutBridgeControl
    val loadWarning: String?

    init {
        val primaryStore = AtomicProjectStore(appContext.filesDir.toPath().resolve("projects/default"))
        val loadResult = primaryStore.load()
        val loadError = loadResult.exceptionOrNull() as? ProjectStoreException
        val activeStore = if (loadResult.isSuccess || loadError?.code == ProjectStoreErrorCode.NOT_FOUND) {
            primaryStore
        } else {
            AtomicProjectStore(
                appContext.filesDir.toPath().resolve("projects/recovery-${System.currentTimeMillis()}"),
            )
        }
        loadWarning = if (activeStore !== primaryStore) {
            "Projeto anterior preservado; abrindo recuperação."
        } else {
            null
        }
        val initialProject = loadResult.getOrNull() ?: Project.create("Meu projeto", 1080, 1920, 30)

        val compositionCompiler = ProjectCompositionCompiler(AssetUriResolver(appContext.contentResolver))
        val exportCapabilitiesProvider = AndroidExportCapabilitiesProvider(appContext)
        previewController = Media3PreviewController(appContext, compositionCompiler)
        exportController = Media3ExportController(
            appContext,
            compositionCompiler,
            appContext.cacheDir,
            capabilitiesProvider = exportCapabilitiesProvider,
        )
        finalPreviewRenderer = FinalPreviewRenderer(
            exportController,
            File(appContext.cacheDir, "final-previews"),
        )
        autosave = DebouncedProjectSaver(activeStore, autosaveScheduler, 450L)

        val previewLoader = object : ProjectPreviewLoader {
            override fun load(project: Project): Result<Unit> = previewController.load(project).recoverCatching { error ->
                if (error is RenderPlanException && error.code == RenderPlanErrorCode.ASSET_UNAVAILABLE) {
                    throw PreviewLoadException(
                        PreviewErrorCode.ASSET_UNAVAILABLE,
                        error.assetId,
                        error.message ?: "asset unavailable",
                        error,
                    )
                }
                throw PreviewLoadException(
                    PreviewErrorCode.PREVIEW_FAILED,
                    null,
                    error.message ?: "preview failed",
                    error,
                )
            }
        }
        session = ProjectSession(
            initialProject = initialProject,
            preview = previewLoader,
            autosave = autosave,
            store = activeStore,
            revisionClock = SharedPreferencesProjectRevisionClock(appContext),
        )
        editorViewModel = EditorViewModel(session, previewController)
        mediaIngestCoordinator = MediaIngestCoordinator(
            importer = AndroidAssetImporter(appContext.contentResolver),
            editorViewModel = editorViewModel,
            sharedMediaStager = SharedMediaStager(appContext),
            persistProject = autosave::flushNow,
        )
        previewController.load(initialProject)

        val playbackPort = object : BridgePlaybackPort {
            override val positionUs: Long
                get() = previewController.state.value.positionUs

            override fun seekToUs(positionUs: Long): Result<Unit> = runCatching {
                previewController.seekToUs(positionUs)
            }
        }
        val bridgeExportPort = Media3BridgeExportPort(
            exportController = exportController,
            scope = processScope,
            targetFactory = AndroidMediaStoreBridgeExportTargetFactory(appContext.contentResolver),
        )
        bridgeCommandExecutor = BridgeCommandExecutor(
            session = session,
            playback = playbackPort,
            jobs = bridgeJobs,
            exportPort = bridgeExportPort,
            previewFramePort = previewController.bridgePreviewFramePort,
        )
        bridgeTermuxAgentPort = AsyncBridgeTermuxAgentTaskPort(
            scope = processScope,
            client = TermuxAgentEngineRuntime.get(appContext).developerAgentClient,
        )
        val deviceId = AndroidKeystoreDeviceIdentityStore()
            .getOrCreatePublicIdentity()
            .deviceId
        bridgeMessageProcessor = BridgeMessageProcessor(
            deviceId = deviceId,
            executor = bridgeCommandExecutor,
            onExecuted = editorViewModel::refreshSnapshot,
            termuxAgentPort = bridgeTermuxAgentPort,
            activeProjectContextProvider = {
                val current = session.state
                BridgeActiveProjectContext(
                    projectId = current.project.id,
                    revision = current.revision,
                )
            },
        )
        bridgeInboundMessageHandler = BridgeInboundMessageHandler { text, reply ->
            mainHandler.post {
                bridgeMessageProcessor.process(text)?.let(reply)
            }
        }
        bridgeControl = LocalCutBridgeControl(appContext, processScope)
    }

    fun flushAutosave() {
        autosave.flushNow()
    }

    companion object {
        @Volatile
        private var instance: LocalCutProcessRuntime? = null

        fun get(context: Context): LocalCutProcessRuntime = instance ?: synchronized(this) {
            instance ?: LocalCutProcessRuntime(context.applicationContext).also { instance = it }
        }
    }
}
