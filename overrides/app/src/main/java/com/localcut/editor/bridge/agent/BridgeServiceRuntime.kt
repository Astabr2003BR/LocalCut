package com.localcut.editor.bridge.agent

import android.content.Context
import com.localcut.editor.bridge.transport.AndroidHandlerBridgeReconnectScheduler
import com.localcut.editor.bridge.transport.BridgeConnectionCoordinator
import com.localcut.editor.bridge.transport.BridgeSessionProvider
import com.localcut.editor.bridge.transport.BridgeSocketClient
import com.localcut.editor.bridge.transport.BridgeSocketListener
import com.localcut.editor.bridge.transport.BridgeSession

object BridgeServiceRuntime {
    @Volatile
    var coordinatorFactory: ((Context, BridgeSettingsStore) -> BridgeConnectionCoordinator)? = null

    fun createCoordinator(context: Context, settingsStore: BridgeSettingsStore): BridgeConnectionCoordinator {
        coordinatorFactory?.let { return it(context.applicationContext, settingsStore) }
        return BridgeConnectionCoordinator(
            socketClient = UnavailableBridgeSocketClient,
            sessionProvider = BridgeSessionProvider { null },
            settingsStore = settingsStore,
            scheduler = AndroidHandlerBridgeReconnectScheduler(),
        )
    }

    private object UnavailableBridgeSocketClient : BridgeSocketClient {
        override fun connect(session: BridgeSession, listener: BridgeSocketListener): Result<Unit> =
            Result.failure(IllegalStateException("bridge transport is not configured"))

        override fun send(text: String): Result<Unit> =
            Result.failure(IllegalStateException("bridge transport is not configured"))

        override fun disconnect(): Result<Unit> = Result.success(Unit)
    }
}
