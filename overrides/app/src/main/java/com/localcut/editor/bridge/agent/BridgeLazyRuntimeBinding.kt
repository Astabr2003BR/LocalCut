package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.transport.BridgeConnectionObserver
import com.localcut.editor.bridge.transport.BridgeInboundMessageHandler

/**
 * Keeps the Bridge socket lifecycle lightweight while the editor UI is not running.
 * The heavy editor runtime is created only when an inbound command actually needs it.
 */
class BridgeLazyRuntimeBinding(
    inboundHandlerProvider: () -> BridgeInboundMessageHandler,
    private val observerIfInitialized: () -> BridgeConnectionObserver?,
) {
    private val inboundHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        inboundHandlerProvider()
    }

    val inboundMessageHandler = BridgeInboundMessageHandler { text, reply ->
        inboundHandler.handle(text, reply)
    }

    val observer: BridgeConnectionObserver = object : BridgeConnectionObserver {
        override fun onConnected() {
            observerIfInitialized()?.onConnected()
        }

        override fun onDisconnected(code: Int?, reason: String?) {
            observerIfInitialized()?.onDisconnected(code, reason)
        }

        override fun onFailure(message: String) {
            observerIfInitialized()?.onFailure(message)
        }
    }
}
