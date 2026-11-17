package com.localcut.editor.bridge.agent

import com.localcut.editor.bridge.transport.BridgeConnectionObserver
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeConnectionStatusRelayTest {
    @Test
    fun connectedBeforeUiAttachesIsReplayed() {
        val relay = BridgeConnectionStatusRelay()
        val events = mutableListOf<String>()

        relay.onConnected()
        relay.attach(recordingObserver(events))

        assertEquals(listOf("connected"), events)
    }

    @Test
    fun onlyLatestPreAttachStateIsReplayed() {
        val relay = BridgeConnectionStatusRelay()
        val events = mutableListOf<String>()

        relay.onConnected()
        relay.onDisconnected(1006, "network")
        relay.onFailure("offline")
        relay.attach(recordingObserver(events))

        assertEquals(listOf("failure:offline"), events)
    }

    @Test
    fun attachedObserverReceivesLiveEventsInOrder() {
        val relay = BridgeConnectionStatusRelay()
        val events = mutableListOf<String>()
        val observer = recordingObserver(events)
        relay.attach(observer)

        relay.onConnected()
        relay.onDisconnected(401, "expired")
        relay.onFailure("offline")

        assertEquals(
            listOf("connected", "disconnected:401:expired", "failure:offline"),
            events,
        )
    }

    @Test
    fun detachedObserverDoesNotReceiveFurtherEventsAndLatestStillReplays() {
        val relay = BridgeConnectionStatusRelay()
        val firstEvents = mutableListOf<String>()
        val first = recordingObserver(firstEvents)
        relay.attach(first)
        relay.onConnected()
        relay.detach(first)
        relay.onDisconnected(1000, "background")

        val secondEvents = mutableListOf<String>()
        relay.attach(recordingObserver(secondEvents))

        assertEquals(listOf("connected"), firstEvents)
        assertEquals(listOf("disconnected:1000:background"), secondEvents)
    }

    private fun recordingObserver(events: MutableList<String>): BridgeConnectionObserver =
        object : BridgeConnectionObserver {
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
}
