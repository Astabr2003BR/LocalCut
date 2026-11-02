package com.localcut.editor.bridge.transport

import android.os.Handler
import android.os.Looper

class AndroidHandlerBridgeReconnectScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : BridgeReconnectScheduler {
    override fun schedule(delayMs: Long, action: () -> Unit): BridgeScheduledTask {
        require(delayMs >= 0L) { "delayMs must be non-negative" }
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return BridgeScheduledTask { handler.removeCallbacks(runnable) }
    }
}
