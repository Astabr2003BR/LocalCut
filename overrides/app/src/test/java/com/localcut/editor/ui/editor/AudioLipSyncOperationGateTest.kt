package com.localcut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLipSyncOperationGateTest {
    @Test
    fun latestOperationOnlyCanApply() {
        val gate = AudioLipSyncOperationGate()
        val first = gate.begin()
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun cancellingPreventsAlreadyPostedCallbackFromApplying() {
        val gate = AudioLipSyncOperationGate()
        val token = gate.begin()
        val pendingUiCallback = { gate.isCurrent(token) }

        gate.cancel()

        assertFalse(pendingUiCallback())
    }

    @Test
    fun disposingInvalidatesQueuedCallbackAndRefusesFurtherWork() {
        val gate = AudioLipSyncOperationGate()
        val token = gate.begin()
        val pendingUiCallback = { gate.isCurrent(token) }

        gate.dispose()

        assertFalse(pendingUiCallback())
        assertFalse(gate.isCurrent(token))
        assertFalse(gate.tryBegin().isSuccess)
    }

    @Test
    fun cancelledRequestCanBeRetriedWithoutReusingOldToken() {
        val gate = AudioLipSyncOperationGate()
        val original = gate.begin()
        gate.cancel()
        val retry = gate.begin()
        assertFalse(gate.isCurrent(original))
        assertTrue(gate.isCurrent(retry))
    }

    @Test
    fun onlyOneThreadCanOwnTheLastOperationToken() {
        val gate = AudioLipSyncOperationGate()
        val token = gate.begin()
        val thread = Thread { gate.cancel() }
        thread.start()
        thread.join(2000L)
        assertFalse(thread.isAlive)
        assertFalse(gate.isCurrent(token))
    }
}
