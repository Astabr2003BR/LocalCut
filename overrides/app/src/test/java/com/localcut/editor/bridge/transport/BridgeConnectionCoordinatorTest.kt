package com.localcut.editor.bridge.transport

import com.localcut.editor.bridge.agent.InMemoryBridgeSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeConnectionCoordinatorTest {
    private val session = BridgeSession("wss://bridge.example/ws", "lc_device", "token")

    @Test
    fun userConnectEnablesSessionAndConnectsImmediately() {
        val fixture = fixture()

        fixture.coordinator.connectFromUser()

        assertTrue(fixture.settings.sessionEnabled)
        assertEquals(1, fixture.socket.connectCalls)
        assertEquals(session.deviceId, fixture.socket.lastSession!!.deviceId)
    }

    @Test
    fun failureSchedulesBoundedReconnectAndSuccessfulConnectResetsAttempt() {
        val fixture = fixture()
        fixture.coordinator.connectFromUser()

        fixture.socket.listener!!.onFailure("network")
        assertEquals(listOf(1_000L), fixture.scheduler.delays)

        fixture.scheduler.runLast()
        assertEquals(2, fixture.socket.connectCalls)
        fixture.socket.listener!!.onFailure("network again")
        assertEquals(listOf(1_000L, 2_000L), fixture.scheduler.delays)

        fixture.scheduler.runLast()
        fixture.socket.listener!!.onConnected()
        fixture.socket.listener!!.onFailure("later")
        assertEquals(1_000L, fixture.scheduler.delays.last())
    }

    @Test
    fun manualDisconnectCancelsReconnectAndKeepsAlwaysConnectedPreference() {
        val fixture = fixture(alwaysConnected = true)
        fixture.coordinator.connectFromUser()
        fixture.socket.listener!!.onFailure("network")
        assertEquals(1, fixture.scheduler.pendingCount)

        fixture.coordinator.disconnectFromUser()

        assertFalse(fixture.settings.sessionEnabled)
        assertTrue(fixture.settings.alwaysConnected)
        assertEquals(0, fixture.scheduler.pendingCount)
        assertEquals(1, fixture.socket.disconnectCalls)
    }

    @Test
    fun missingSessionDoesNotSpinReconnectLoop() {
        val fixture = fixture(sessionProvider = BridgeSessionProvider { null })

        val result = fixture.coordinator.connectFromUser()

        assertTrue(result.isFailure)
        assertEquals(0, fixture.socket.connectCalls)
        assertEquals(0, fixture.scheduler.pendingCount)
    }

    private data class Fixture(
        val coordinator: BridgeConnectionCoordinator,
        val socket: FakeSocketClient,
        val scheduler: FakeScheduler,
        val settings: InMemoryBridgeSettingsStore,
    )

    private fun fixture(
        alwaysConnected: Boolean = false,
        sessionProvider: BridgeSessionProvider = BridgeSessionProvider { session },
    ): Fixture {
        val socket = FakeSocketClient()
        val scheduler = FakeScheduler()
        val settings = InMemoryBridgeSettingsStore(alwaysConnected = alwaysConnected)
        val coordinator = BridgeConnectionCoordinator(
            socketClient = socket,
            sessionProvider = sessionProvider,
            settingsStore = settings,
            reconnectPolicy = BridgeReconnectPolicy(jitterFraction = 0.0),
            scheduler = scheduler,
            randomUnit = { 0.5 },
        )
        return Fixture(coordinator, socket, scheduler, settings)
    }

    private class FakeSocketClient : BridgeSocketClient {
        var connectCalls = 0
        var disconnectCalls = 0
        var lastSession: BridgeSession? = null
        var listener: BridgeSocketListener? = null

        override fun connect(session: BridgeSession, listener: BridgeSocketListener): Result<Unit> = runCatching {
            connectCalls += 1
            lastSession = session
            this.listener = listener
        }

        override fun send(text: String): Result<Unit> = Result.success(Unit)

        override fun disconnect(): Result<Unit> = runCatching {
            disconnectCalls += 1
        }
    }

    private class FakeScheduler : BridgeReconnectScheduler {
        private data class Entry(val delayMs: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private val entries = mutableListOf<Entry>()
        val delays: List<Long> get() = entries.map { it.delayMs }
        val pendingCount: Int get() = entries.count { !it.cancelled }

        override fun schedule(delayMs: Long, action: () -> Unit): BridgeScheduledTask {
            val entry = Entry(delayMs, action)
            entries += entry
            return BridgeScheduledTask { entry.cancelled = true }
        }

        fun runLast() {
            val entry = entries.last { !it.cancelled }
            entry.cancelled = true
            entry.action()
        }
    }
}
