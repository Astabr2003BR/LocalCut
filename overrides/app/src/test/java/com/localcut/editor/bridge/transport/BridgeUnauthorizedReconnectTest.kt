package com.localcut.editor.bridge.transport

import com.localcut.editor.bridge.agent.InMemoryBridgeSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeUnauthorizedReconnectTest {
    @Test
    fun unauthorizedDoesNotRetrySameRejectedSession() {
        val socket = FakeSocketClient()
        val scheduler = FakeScheduler()
        val settings = InMemoryBridgeSettingsStore(sessionEnabled = true, alwaysConnected = true)
        val coordinator = BridgeConnectionCoordinator(
            socketClient = socket,
            sessionProvider = BridgeSessionProvider {
                BridgeSession("wss://bridge.example/ws", "lc_device", "rejected-token")
            },
            settingsStore = settings,
            reconnectPolicy = BridgeReconnectPolicy(jitterFraction = 0.0),
            scheduler = scheduler,
            randomUnit = { 0.5 },
        )

        coordinator.connectFromUser()
        socket.listener!!.onDisconnected(401, "Unauthorized")

        assertEquals(
            "an HTTP 401 must not schedule another connection with the same rejected access token",
            0,
            scheduler.pendingCount,
        )
    }

    private class FakeSocketClient : BridgeSocketClient {
        var listener: BridgeSocketListener? = null

        override fun connect(session: BridgeSession, listener: BridgeSocketListener): Result<Unit> = runCatching {
            this.listener = listener
        }

        override fun send(text: String): Result<Unit> = Result.success(Unit)
        override fun disconnect(): Result<Unit> = Result.success(Unit)
    }

    private class FakeScheduler : BridgeReconnectScheduler {
        private data class Entry(var cancelled: Boolean = false)
        private val entries = mutableListOf<Entry>()
        val pendingCount: Int get() = entries.count { !it.cancelled }

        override fun schedule(delayMs: Long, action: () -> Unit): BridgeScheduledTask {
            val entry = Entry()
            entries += entry
            return BridgeScheduledTask { entry.cancelled = true }
        }
    }
}
