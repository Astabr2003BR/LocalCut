package com.localcut.editor.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.ui.PlayerView
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.timeline.TimelineMath
import java.io.File

@UnstableApi
@OptIn(ExperimentalApi::class)
class Media3PreviewController(
    context: Context,
    compositionCompiler: ProjectCompositionCompiler,
    runtimeProvider: PreviewRuntimeProvider = AndroidPreviewRuntimeProvider(context),
) : PreviewController {
    private val player = CompositionPlayer.Builder(context).build()
    private val engine = Media3PreviewEngine(player, compositionCompiler, runtimeProvider)
    private val delegate = DefaultPreviewController(engine, AndroidPreviewTicker())

    override val state = delegate.state
    override fun load(project: Project): Result<Unit> = delegate.load(project)

    fun loadRenderedFile(file: File, durationUs: Long, fps: Int): Result<Unit> =
        delegate.loadResult(engine.loadRenderedFile(file, durationUs, fps))
    override fun play() = delegate.play()
    override fun pause() = delegate.pause()
    override fun seekToUs(positionUs: Long) = delegate.seekToUs(positionUs)
    override fun stepFrames(deltaFrames: Int, fps: Int) = delegate.stepFrames(deltaFrames, fps)
    override fun setLoopRange(startUs: Long, endUs: Long?) = delegate.setLoopRange(startUs, endUs)
    override fun release() = delegate.release()

    fun bindTo(view: PlayerView) {
        view.player = player
        view.useController = false
    }

    fun unbindFrom(view: PlayerView) {
        if (view.player === player) view.player = null
    }
}

@UnstableApi
@OptIn(ExperimentalApi::class)
private class Media3PreviewEngine(
    private val player: CompositionPlayer,
    private val compiler: ProjectCompositionCompiler,
    private val runtimeProvider: PreviewRuntimeProvider,
) : PreviewEngine {
    private var errorCallback: (String) -> Unit = {}

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorCallback(error.message ?: "Media3 playback error")
            }
        })
    }

    override fun load(project: Project): Result<PreviewLoadResult> {
        val durationUs = TimelineMath.durationUs(project)
        if (durationUs == 0L) {
            player.pause()
            player.stop()
            return Result.success(PreviewLoadResult(0L, project.fps))
        }
        val outputSettings = PreviewRenderSettingsResolver.resolve(
            project = project,
            runtime = runtimeProvider.current(),
        ).outputSettings
        return compiler.compile(
            project = project,
            mode = RenderMode.FAST_PREVIEW,
            outputSettings = outputSettings,
        ).map { composition ->
            player.setComposition(composition)
            player.prepare()
            PreviewLoadResult(durationUs, project.fps)
        }
    }

    fun loadRenderedFile(file: File, durationUs: Long, fps: Int): Result<PreviewLoadResult> = runCatching {
        require(file.isFile && file.length() > 0L) { "Rendered preview file is missing or empty" }
        require(durationUs > 0L) { "Rendered preview duration must be > 0" }
        require(fps > 0) { "Rendered preview fps must be > 0" }
        val mediaItem = MediaItem.Builder().setUri(file.toURI().toString()).build()
        val edited = EditedMediaItem.Builder(mediaItem).setDurationUs(durationUs).build()
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))
        player.setComposition(Composition.Builder(sequence).build())
        player.prepare()
        PreviewLoadResult(durationUs, fps)
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekToUs(positionUs: Long) = player.seekTo(positionUs.coerceAtLeast(0L) / 1_000L)
    override fun currentPositionUs(): Long = player.currentPosition * 1_000L
    override fun setErrorCallback(callback: (String) -> Unit) { errorCallback = callback }
    override fun release() = player.release()
}

private class AndroidPreviewTicker(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : PreviewTicker {
    private var callback: (() -> Unit)? = null
    private val runnable = object : Runnable {
        override fun run() {
            callback?.invoke()
            if (callback != null) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun start(onTick: () -> Unit) {
        stop()
        callback = onTick
        handler.post(runnable)
    }

    override fun stop() {
        callback = null
        handler.removeCallbacks(runnable)
    }

    private companion object { const val TICK_MS = 33L }
}
