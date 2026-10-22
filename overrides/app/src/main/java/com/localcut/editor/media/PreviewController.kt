package com.localcut.editor.media

import com.localcut.editor.domain.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PreviewState(
    val positionUs: Long = 0L,
    val durationUs: Long = 0L,
    val fps: Int = 30,
    val playing: Boolean = false,
    val errorMessage: String? = null,
)

data class PreviewLoadResult(val durationUs: Long, val fps: Int)

interface PreviewController {
    val state: StateFlow<PreviewState>
    fun load(project: Project): Result<Unit>
    fun play()
    fun pause()
    fun seekToUs(positionUs: Long)
    fun stepFrames(deltaFrames: Int, fps: Int = state.value.fps)
    fun setLoopRange(startUs: Long, endUs: Long?)
    fun release()
}

interface PreviewEngine {
    fun load(project: Project): Result<PreviewLoadResult>
    fun play()
    fun pause()
    fun seekToUs(positionUs: Long)
    fun currentPositionUs(): Long
    fun setErrorCallback(callback: (String) -> Unit)
    fun release()
}

interface PreviewTicker {
    fun start(onTick: () -> Unit)
    fun stop()
}

class DefaultPreviewController(
    private val engine: PreviewEngine,
    private val ticker: PreviewTicker,
) : PreviewController {
    private val transport = PreviewTransport()
    private val mutableState = MutableStateFlow(PreviewState())
    override val state: StateFlow<PreviewState> = mutableState.asStateFlow()

    init {
        engine.setErrorCallback { message ->
            mutableState.value = mutableState.value.copy(errorMessage = message, playing = false)
            transport.pause()
        }
    }

    override fun load(project: Project): Result<Unit> = loadResult(engine.load(project))

    internal fun loadResult(result: Result<PreviewLoadResult>): Result<Unit> = result
        .onFailure { error ->
            ticker.stop()
            transport.pause()
            mutableState.value = mutableState.value.copy(
                playing = false,
                errorMessage = error.message ?: "Falha ao carregar a prévia",
            )
        }
        .map { loaded ->
            transport.load(loaded.durationUs, loaded.fps)
            mutableState.value = PreviewState(durationUs = loaded.durationUs, fps = loaded.fps)
            ticker.stop()
            if (loaded.durationUs > 0L) ticker.start(::pollPosition)
        }

    override fun play() {
        if (mutableState.value.errorMessage != null) return
        transport.play()
        engine.play()
        publishTransportState()
    }

    override fun pause() {
        transport.pause()
        engine.pause()
        publishTransportState()
    }

    override fun seekToUs(positionUs: Long) {
        val target = transport.seekToUs(positionUs)
        engine.seekToUs(target)
        publishTransportState()
    }

    override fun stepFrames(deltaFrames: Int, fps: Int) {
        require(fps > 0) { "fps must be > 0" }
        if (transport.state.playing) engine.pause()
        val target = PreviewFrameMath.step(
            positionUs = transport.state.positionUs,
            deltaFrames = deltaFrames,
            fps = fps,
            durationUs = transport.state.durationUs,
        )
        transport.pause()
        transport.seekToUs(target)
        engine.seekToUs(target)
        publishTransportState()
    }

    override fun setLoopRange(startUs: Long, endUs: Long?) {
        transport.setLoopRange(startUs, endUs)
        publishTransportState()
    }

    override fun release() {
        ticker.stop()
        transport.pause()
        engine.release()
        publishTransportState()
    }

    private fun pollPosition() {
        try {
            val seekTarget = transport.onPlayerPosition(engine.currentPositionUs())
            if (seekTarget != null) engine.seekToUs(seekTarget)
            publishTransportState()
        } catch (error: Throwable) {
            ticker.stop()
            transport.pause()
            mutableState.value = mutableState.value.copy(
                playing = false,
                errorMessage = error.message ?: "Falha ao atualizar a prévia",
            )
        }
    }

    private fun publishTransportState() {
        val t = transport.state
        mutableState.value = mutableState.value.copy(
            positionUs = t.positionUs,
            durationUs = t.durationUs,
            fps = t.fps,
            playing = t.playing,
        )
    }
}
