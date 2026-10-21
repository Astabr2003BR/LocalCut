package com.localcut.editor.bridge.transport

import com.localcut.editor.bridge.agent.BridgeSettingsStore

fun interface BridgeScheduledTask {
    fun cancel()
}

fun interface BridgeReconnectScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): BridgeScheduledTask
}

fun interface BridgeInboundMessageHandler {
    fun handle(text: String, reply: (String) -> Unit)
}

interface BridgeConnectionObserver {
    fun onConnected() = Unit
    fun onDisconnected(code: Int?, reason: String?) = Unit
    fun onFailure(message: String) = Unit
}

class BridgeConnectionCoordinator(
    private val socketClient: BridgeSocketClient,
    private val sessionProvider: BridgeSessionProvider,
    private val settingsStore: BridgeSettingsStore,
    private val reconnectPolicy: BridgeReconnectPolicy = BridgeReconnectPolicy(),
    private val scheduler: BridgeReconnectScheduler,
    private val randomUnit: () -> Double = Math::random,
    private val inboundMessageHandler: BridgeInboundMessageHandler = BridgeInboundMessageHandler { _, _ -> },
    private val observer: BridgeConnectionObserver = object : BridgeConnectionObserver {},
) : BridgeSocketListener {
    private var reconnectAttempt = 0
    private var scheduledReconnect: BridgeScheduledTask? = null
    private var manuallyDisconnected = false

    @Synchronized
    fun connectFromUser(): Result<Unit> {
        manuallyDisconnected = false
        settingsStore.sessionEnabled = true
        reconnectAttempt = 0
        cancelScheduledReconnect()
        return connectNow(scheduleOnTransportFailure = true)
    }

    @Synchronized
    fun reconnectIfEnabled(): Result<Unit> {
        manuallyDisconnected = false
        if (!settingsStore.sessionEnabled) {
            return Result.failure(IllegalStateException("bridge session is disabled"))
        }
        cancelScheduledReconnect()
        return connectNow(scheduleOnTransportFailure = true)
    }

    @Synchronized
    fun disconnectFromUser(): Result<Unit> {
        manuallyDisconnected = true
        settingsStore.markManualDisconnect()
        reconnectAttempt = 0
        cancelScheduledReconnect()
        return socketClient.disconnect()
    }

    @Synchronized
    override fun onConnected() {
        reconnectAttempt = 0
        cancelScheduledReconnect()
        observer.onConnected()
    }

    override fun onTextMessage(text: String) {
        inboundMessageHandler.handle(text) { response ->
            socketClient.send(response).onFailure {
                onFailure(it.message ?: "bridge reply failed")
            }
        }
    }

    @Synchronized
    override fun onDisconnected(code: Int?, reason: String?) {
        observer.onDisconnected(code, reason)
        scheduleReconnectIfAllowed()
    }

    @Synchronized
    override fun onFailure(message: String) {
        observer.onFailure(message)
        scheduleReconnectIfAllowed()
    }

    @Synchronized
    fun shutdown(): Result<Unit> {
        cancelScheduledReconnect()
        return socketClient.disconnect()
    }

    private fun connectNow(scheduleOnTransportFailure: Boolean): Result<Unit> {
        val session = sessionProvider.currentSession()
            ?: return Result.failure(IllegalStateException("paired bridge session is unavailable or expired"))
        val result = socketClient.connect(session, this)
        if (result.isFailure && scheduleOnTransportFailure) {
            observer.onFailure(result.exceptionOrNull()?.message ?: "bridge connect failed")
            scheduleReconnectIfAllowed()
        }
        return result
    }

    private fun scheduleReconnectIfAllowed() {
        if (scheduledReconnect != null) return
        if (!reconnectPolicy.shouldReconnect(manuallyDisconnected, settingsStore.sessionEnabled)) return
        val delayMs = reconnectPolicy.delayMs(
            attempt = reconnectAttempt,
            randomUnit = randomUnit().coerceIn(0.0, 1.0),
        )
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(62)
        lateinit var task: BridgeScheduledTask
        task = scheduler.schedule(delayMs) {
            synchronized(this) {
                if (scheduledReconnect === task) scheduledReconnect = null
                if (!reconnectPolicy.shouldReconnect(manuallyDisconnected, settingsStore.sessionEnabled)) return@synchronized
                connectNow(scheduleOnTransportFailure = true)
            }
        }
        scheduledReconnect = task
    }

    private fun cancelScheduledReconnect() {
        scheduledReconnect?.cancel()
        scheduledReconnect = null
    }
}
