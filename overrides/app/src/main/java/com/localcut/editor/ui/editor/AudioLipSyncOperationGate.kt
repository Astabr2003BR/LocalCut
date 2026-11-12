package com.localcut.editor.ui.editor

/** Guards audio-analysis UI callbacks against cancellation, navigation, and clip changes. */
class AudioLipSyncOperationGate {
    class Token internal constructor()

    private var current: Token? = null
    private var disposed = false

    @Synchronized
    fun tryBegin(): Result<Token> {
        if (disposed) return Result.failure(IllegalStateException("Lip-sync panel is no longer active"))
        return Result.success(Token().also { current = it })
    }

    @Synchronized
    fun begin(): Token = tryBegin().getOrThrow()

    @Synchronized
    fun isCurrent(token: Token): Boolean = !disposed && current === token

    @Synchronized
    fun cancel() {
        current = null
    }

    @Synchronized
    fun dispose() {
        current = null
        disposed = true
    }
}
