package com.localcut.editor.bridge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class BridgeServiceLifecycleGuardTest {
    @Test
    fun destroyBeforeCoordinatorUseDoesNotCreateCoordinator() {
        var created = 0
        var shutdowns = 0
        val lifecycle = BridgeCoordinatorLifecycle(
            factory = {
                created += 1
                Any()
            },
            shutdownAction = { shutdowns += 1 },
        )

        lifecycle.shutdownIfInitialized()

        assertEquals(0, created)
        assertEquals(0, shutdowns)
    }

    @Test
    fun destroyAfterCoordinatorUseShutsDownExactlyOnce() {
        var created = 0
        var shutdowns = 0
        val resource = Any()
        val lifecycle = BridgeCoordinatorLifecycle(
            factory = {
                created += 1
                resource
            },
            shutdownAction = { shutdowns += 1 },
        )

        assertSame(resource, lifecycle.value)
        lifecycle.shutdownIfInitialized()
        lifecycle.shutdownIfInitialized()

        assertEquals(1, created)
        assertEquals(1, shutdowns)
    }

    @Test
    fun foregroundStartRuntimeFailureIsContainedInsteadOfEscaping() {
        var failureMessage: String? = null

        val started = BridgeForegroundServiceGuard.tryStart(
            start = { throw IllegalStateException("background FGS blocked") },
            onFailure = { failureMessage = it.message },
        )

        assertFalse(started)
        assertEquals("background FGS blocked", failureMessage)
    }
}
