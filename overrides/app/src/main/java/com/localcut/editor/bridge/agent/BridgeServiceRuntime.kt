package com.localcut.editor.bridge.agent

import android.content.Context
import com.localcut.editor.LocalCutProcessRuntime
import com.localcut.editor.bridge.security.AndroidKeystoreBridgeCredentialStore
import com.localcut.editor.bridge.security.AndroidKeystoreDeviceIdentityStore
import com.localcut.editor.bridge.transport.AndroidHandlerBridgeReconnectScheduler
import com.localcut.editor.bridge.transport.BridgeConnectionCoordinator
import com.localcut.editor.bridge.transport.BridgeEndpointConfig
import com.localcut.editor.bridge.transport.BridgeSession
import com.localcut.editor.bridge.transport.BridgeSessionProvider
import com.localcut.editor.bridge.transport.BridgeSocketClient
import com.localcut.editor.bridge.transport.BridgeSocketListener
import com.localcut.editor.bridge.transport.OkHttpBridgeSocketClient
import com.localcut.editor.bridge.transport.StoredBridgeSessionProvider
import com.localcut.editor.diagnostics.ProcessExitDiagnostics

object BridgeServiceRuntime {
    @Volatile
    var coordinatorFactory: ((Context, BridgeSettingsStore) -> BridgeConnectionCoordinator)? = null

    fun createCoordinator(context: Context, settingsStore: BridgeSettingsStore): BridgeConnectionCoordinator {
        val appContext = context.applicationContext
        coordinatorFactory?.let { return it(appContext, settingsStore) }
        val endpoint = BridgeEndpointConfig.resolve(settingsStore.relayBaseUrl).getOrNull()
            ?: return unavailableCoordinator(settingsStore)

        ProcessExitDiagnostics.mark(appContext, "bridge-runtime:before-editor-runtime")
        val editorRuntime = LocalCutProcessRuntime.get(appContext)
        ProcessExitDiagnostics.mark(appContext, "bridge-runtime:editor-runtime-ready")
        val socketClient = OkHttpBridgeSocketClient()
        val sessionProvider = StoredBridgeSessionProvider(
            relayUrl = endpoint.wssUrl,
            identityStore = AndroidKeystoreDeviceIdentityStore(),
            credentialStore = AndroidKeystoreBridgeCredentialStore(appContext),
        )
        return BridgeConnectionCoordinator(
            socketClient = socketClient,
            sessionProvider = sessionProvider,
            settingsStore = settingsStore,
            scheduler = AndroidHandlerBridgeReconnectScheduler(),
            inboundMessageHandler = editorRuntime.bridgeInboundMessageHandler,
            observer = editorRuntime.bridgeControl.connectionObserver,
        )
    }

    private fun unavailableCoordinator(settingsStore: BridgeSettingsStore): BridgeConnectionCoordinator =
        BridgeConnectionCoordinator(
            socketClient = UnavailableBridgeSocketClient,
            sessionProvider = BridgeSessionProvider { null },
            settingsStore = settingsStore,
            scheduler = AndroidHandlerBridgeReconnectScheduler(),
        )

    private object UnavailableBridgeSocketClient : BridgeSocketClient {
        override fun connect(session: BridgeSession, listener: BridgeSocketListener): Result<Unit> =
            Result.failure(IllegalStateException("bridge transport is not configured"))

        override fun send(text: String): Result<Unit> =
            Result.failure(IllegalStateException("bridge transport is not configured"))

        override fun disconnect(): Result<Unit> = Result.success(Unit)
    }
}
