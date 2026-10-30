package com.localcut.editor.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.localcut.editor.domain.model.Project
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

@UnstableApi
class Media3ExportController(
    context: Context,
    private val compositionCompiler: ProjectCompositionCompiler,
    private val cacheDir: File,
    private val capabilitiesProvider: ExportCapabilitiesProvider = AndroidExportCapabilitiesProvider(context),
) : ExportController {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var active: ActiveExport? = null

    override fun export(project: Project, destination: ExportDestination): Flow<ExportEvent> =
        export(project, destination, ExportProfile.defaultFor(project))

    override fun export(
        project: Project,
        destination: ExportDestination,
        profile: ExportProfile,
    ): Flow<ExportEvent> = callbackFlow {
        val settings = ExportPreparation.prepare(
            project = project,
            requestedProfile = profile,
            capabilities = capabilitiesProvider.current(),
        ).getOrElse { error ->
            withContext(Dispatchers.IO) { destination.discard() }
            trySend(ExportEvent.Failed(error.message ?: "Invalid export settings", error))
            close()
            return@callbackFlow
        }

        val tempFile = try {
            withContext(Dispatchers.IO) {
                cacheDir.mkdirs()
                File.createTempFile("localcut-export-", ".mp4.tmp", cacheDir)
            }
        } catch (error: Throwable) {
            withContext(Dispatchers.IO) { destination.discard() }
            trySend(ExportEvent.Failed("Unable to create export temporary file", error))
            close()
            return@callbackFlow
        }

        var registeredSession: ActiveExport? = null
        try {
            val coordinator = ExportLifecycleCoordinator(tempFile, destination) { event -> trySend(event) }
            val session = ActiveExport(
                coordinator = coordinator,
                closeFlow = { close() },
            )
            registeredSession = session

            val accepted = synchronized(lock) {
                if (active != null) false else {
                    active = session
                    true
                }
            }
            if (!accepted) {
                tempFile.delete()
                withContext(Dispatchers.IO) { destination.discard() }
                trySend(ExportEvent.Failed("Another export is already running"))
                close()
                return@callbackFlow
            }

            coordinator.started()
            val composition = withContext(Dispatchers.IO) {
                compositionCompiler.compile(
                    project = project,
                    mode = RenderMode.EXPORT,
                    outputSettings = settings.outputSettings,
                )
            }.getOrElse { error ->
                withContext(Dispatchers.IO) { coordinator.renderFailed(error) }
                finishSession(session)
                return@callbackFlow
            }

            withContext(Dispatchers.Main.immediate) {
                if (!isActiveSession(session) || coordinator.isTerminal()) return@withContext
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        session.renderFinished = true
                        stopProgress(session)
                        cleanupExecutor.execute {
                            coordinator.renderCompleted()
                            finishSession(session)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        session.renderFinished = true
                        stopProgress(session)
                        cleanupExecutor.execute {
                            coordinator.renderFailed(exportException)
                            finishSession(session)
                        }
                    }
                }
                val encoderFactory = DefaultEncoderFactory.Builder(appContext)
                    .setEnableFallback(settings.enableEncoderFallback)
                    .setEnableFormatFallback(settings.enableFormatFallback)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(settings.requestedVideoBitrateBitsPerSecond)
                            .build()
                    )
                    .build()
                val transformerBuilder = Transformer.Builder(appContext)
                    .setVideoMimeType(settings.videoMimeType)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                val transformer = LocalCutFrameProcessorIntegration.configureTransformerBuilder(
                    builder = transformerBuilder,
                    context = appContext,
                    project = project,
                ).build()
                session.transformer = transformer
                session.progressRunnable = createProgressRunnable(session, transformer)
                transformer.start(composition, tempFile.absolutePath)
                mainHandler.post(session.progressRunnable!!)
            }

            awaitClose { cancelSession(session) }
        } finally {
            registeredSession?.let(::cancelSession)
        }
    }

    override fun cancel() {
        val session = synchronized(lock) { active } ?: return
        cancelSession(session)
    }

    private fun createProgressRunnable(session: ActiveExport, transformer: Transformer): Runnable = object : Runnable {
        private val holder = ProgressHolder()
        override fun run() {
            if (!isActiveSession(session) || session.renderFinished || session.coordinator.isTerminal()) return
            val state = transformer.getProgress(holder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) session.coordinator.progress(holder.progress)
            if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                mainHandler.postDelayed(this, PROGRESS_POLL_MS)
            }
        }
    }

    private fun cancelSession(session: ActiveExport) {
        val cancellable = synchronized(lock) {
            if (
                active === session &&
                !session.renderFinished &&
                !session.coordinator.isTerminal() &&
                !session.cancellationRequested
            ) {
                session.cancellationRequested = true
                true
            } else {
                false
            }
        }
        if (!cancellable) return
        mainHandler.post {
            if (!isActiveSession(session) || session.renderFinished) return@post
            session.transformer?.cancel()
            stopProgress(session)
            cleanupExecutor.execute {
                session.coordinator.cancel()
                finishSession(session)
            }
        }
    }

    private fun stopProgress(session: ActiveExport) {
        session.progressRunnable?.let(mainHandler::removeCallbacks)
        session.progressRunnable = null
    }

    private fun finishSession(session: ActiveExport) {
        synchronized(lock) {
            if (active === session) active = null
        }
        session.closeFlow()
    }

    private fun isActiveSession(session: ActiveExport): Boolean = synchronized(lock) { active === session }

    private class ActiveExport(
        val coordinator: ExportLifecycleCoordinator,
        val closeFlow: () -> Unit,
    ) {
        var transformer: Transformer? = null
        var progressRunnable: Runnable? = null
        var cancellationRequested: Boolean = false
        @Volatile var renderFinished: Boolean = false
    }

    private companion object {
        const val PROGRESS_POLL_MS = 250L
        val cleanupExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "localcut-export-cleanup").apply { isDaemon = true }
        }
    }
}
