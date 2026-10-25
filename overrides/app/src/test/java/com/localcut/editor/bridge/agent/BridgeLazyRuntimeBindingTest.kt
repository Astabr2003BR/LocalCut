package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.transport.BridgeConnectionObserver
import com.localcut.editor.bridge.transport.BridgeInboundMessageHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeLazyRuntimeBindingTest {
    @Test
    fun socketLifecycleDoesNotInitializeHeavyInboundRuntime() {
        var inboundProviderCalls = 0
        var observerProviderCalls = 0
        val binding = BridgeLazyRuntimeBinding(
            inboundHandlerProvider = {
                inboundProviderCalls += 1
                BridgeInboundMessageHandler { _, _ -> }
            },
            observerIfInitialized = {
                observerProviderCalls += 1
                null
            },
        )

        binding.observer.onConnected()
        binding.observer.onDisconnected(1000, "background")
        binding.observer.onFailure("network")

        assertEquals(0, inboundProviderCalls)
        assertEquals(3, observerProviderCalls)
    }

    @Test
    fun firstInboundMessageInitializesRuntimeOnceAndDelegatesAllMessages() {
        var inboundProviderCalls = 0
        val received = mutableListOf<String>()
        val replies = mutableListOf<String>()
        val binding = BridgeLazyRuntimeBinding(
            inboundHandlerProvider = {
                inboundProviderCalls += 1
                BridgeInboundMessageHandler { text, reply ->
                    received += text
                    reply("reply:$text")
                }
            },
            observerIfInitialized = { null },
        )

        assertEquals(0, inboundProviderCalls)

        binding.inboundMessageHandler.handle("one") { replies += it }
        binding.inboundMessageHandler.handle("two") { replies += it }

        assertEquals(1, inboundProviderCalls)
        assertEquals(listOf("one", "two"), received)
        assertEquals(listOf("reply:one", "reply:two"), replies)
    }

    @Test
    fun observerForwardsOnlyToRuntimeThatAlreadyExists() {
        val events = mutableListOf<String>()
        var currentObserver: BridgeConnectionObserver? = null
        val binding = BridgeLazyRuntimeBinding(
            inboundHandlerProvider = { BridgeInboundMessageHandler { _, _ -> } },
            observerIfInitialized = { currentObserver },
        )

        binding.observer.onConnected()
        assertEquals(emptyList<String>(), events)

        currentObserver = object : BridgeConnectionObserver {
            override fun onConnected() {
                events += "connected"
            }

            override fun onDisconnected(code: Int?, reason: String?) {
                events += "disconnected:$code:$reason"
            }

            override fun onFailure(message: String) {
                events += "failure:$message"
            }
        }

        binding.observer.onConnected()
        binding.observer.onDisconnected(401, "expired")
        binding.observer.onFailure("offline")

        assertEquals(
            listOf("connected", "disconnected:401:expired", "failure:offline"),
            events,
        )
    }
}
