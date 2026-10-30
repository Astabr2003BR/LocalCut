package com.localcut.editor.bridge.agent

internal class BridgeCoordinatorLifecycle<T>(
    factory: () -> T,
    private val shutdownAction: (T) -> Unit,
) {
    private val resourceDelegate: Lazy<T> = lazy(factory)
    private var shutdownDone = false

    val value: T
        get() = resourceDelegate.value

    val isInitialized: Boolean
        get() = resourceDelegate.isInitialized()

    @Synchronized
    fun shutdownIfInitialized() {
        if (shutdownDone || !resourceDelegate.isInitialized()) return
        shutdownDone = true
        shutdownAction(resourceDelegate.value)
    }
}

internal object BridgeForegroundServiceGuard {
    fun tryStart(
        start: () -> Unit,
        onFailure: (RuntimeException) -> Unit,
    ): Boolean = try {
        start()
        true
    } catch (error: RuntimeException) {
        onFailure(error)
        false
    }
}
