package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.transport.BridgeConnectionObserver

/**
 * Lightweight process-level bridge connection state relay.
 *
 * The foreground service can connect before the heavy editor runtime exists. Keeping the latest
 * connection event here prevents the UI from missing CONNECTED/DISCONNECTED/FAILURE callbacks
 * while preserving lazy editor initialization.
 */
class BridgeConnectionStatusRelay : BridgeConnectionObserver {
    private val lock = Any()
    private var latest: Event? = null
    private var observer: BridgeConnectionObserver? = null

    fun attach(observer: BridgeConnectionObserver) {
        synchronized(lock) {
            this.observer = observer
            latest?.dispatch(observer)
        }
    }

    fun detach(observer: BridgeConnectionObserver) {
        synchronized(lock) {
            if (this.observer === observer) this.observer = null
        }
    }

    override fun onConnected() {
        publish(Event.Connected)
    }

    override fun onDisconnected(code: Int?, reason: String?) {
        publish(Event.Disconnected(code, reason))
    }

    override fun onFailure(message: String) {
        publish(Event.Failure(message))
    }

    private fun publish(event: Event) {
        synchronized(lock) {
            latest = event
            observer?.let(event::dispatch)
        }
    }

    private sealed interface Event {
        fun dispatch(observer: BridgeConnectionObserver)

        data object Connected : Event {
            override fun dispatch(observer: BridgeConnectionObserver) = observer.onConnected()
        }

        data class Disconnected(
            val code: Int?,
            val reason: String?,
        ) : Event {
            override fun dispatch(observer: BridgeConnectionObserver) = observer.onDisconnected(code, reason)
        }

        data class Failure(
            val message: String,
        ) : Event {
            override fun dispatch(observer: BridgeConnectionObserver) = observer.onFailure(message)
        }
    }
}

object BridgeProcessConnectionStatus {
    val relay = BridgeConnectionStatusRelay()
}
