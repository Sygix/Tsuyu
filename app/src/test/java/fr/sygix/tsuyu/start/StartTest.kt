/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch

@OptIn(ExperimentalCoroutinesApi::class)
class StartTest {
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }
    private class Settings(var enabled: Debugging = Debugging.ENABLED) : SystemSettings {
        override fun read() = true to enabled
    }
    private class Server(var running: Boolean = false) : ServerAccess { override suspend fun alive() = running }
    private fun launcher(block: suspend ((Phase) -> Unit) -> Unit) = object : ServerLauncher {
        override suspend fun launch(progress: (Phase) -> Unit) = block(progress)
    }
    @Test fun liveBinderIsAuthoritativeEvenWhenDebuggingIsDisabled() = runTest {
        val coordinator = StartCoordinator(Settings(Debugging.DISABLED), Server(true), launcher { error("Must not start") }, direct)
        coordinator.start()
        assertEquals(Phase.RUNNING, coordinator.state.value.phase)
        coordinator.died()
        assertEquals(Phase.STOPPED, coordinator.state.value.phase)
        coordinator.refresh()
        assertEquals(Phase.RUNNING, coordinator.state.value.phase)
    }
    @Test fun disabledAndUnknownSettingsNeverLaunch() = runTest {
        for (debugging in listOf(Debugging.DISABLED, Debugging.UNKNOWN)) {
            val coordinator = StartCoordinator(Settings(debugging), Server(), launcher { error("Must not start") }, direct)
            coordinator.start()
            assertEquals(Phase.SETUP, coordinator.state.value.phase)
        }
    }
    @Test fun concurrentStartsAndLateCompletionConverge() = runTest {
        val gate = CompletableDeferred<Unit>()
        val server = Server()
        var launches = 0
        val coordinator = StartCoordinator(Settings(), server, launcher { progress ->
            launches++; progress(Phase.AUTHORIZING); gate.await(); server.running = true
        }, direct)
        val first = launch { coordinator.start() }
        runCurrent()
        coordinator.start()
        assertEquals(1, launches)
        assertEquals(Phase.AUTHORIZING, coordinator.state.value.phase)
        gate.complete(Unit); first.join()
        assertEquals(Phase.RUNNING, coordinator.state.value.phase)
    }
    @Test fun cancelledLaunchReconcilesActualLateServer() = runTest {
        val server = Server()
        var late: ((Phase) -> Unit)? = null
        val coordinator = StartCoordinator(Settings(), server, launcher { late = it; awaitCancellation() }, direct)
        val job = launch { coordinator.start() }
        runCurrent(); job.cancelAndJoin()
        assertEquals(Phase.STOPPED, coordinator.state.value.phase)
        late?.invoke(Phase.LAUNCHING)
        assertEquals(Phase.STOPPED, coordinator.state.value.phase)
        server.running = true
        coordinator.refresh()
        assertEquals(Phase.RUNNING, coordinator.state.value.phase)
    }
    @Test fun launchWithoutHandshakeAndTypedFailuresNeverClaimRunning() = runTest {
        for (failure in Failure.entries) {
            val coordinator = StartCoordinator(Settings(), Server(), launcher { throw StartException(failure) }, direct)
            coordinator.start()
            assertEquals(Phase.ERROR, coordinator.state.value.phase)
            assertEquals(failure, coordinator.state.value.failure)
        }
        val coordinator = StartCoordinator(Settings(), Server(), launcher {}, direct)
        coordinator.start()
        assertEquals(Failure.LAUNCH, coordinator.state.value.failure)
    }
    @Test fun rebootRefreshInvalidatesPreviouslyRunningState() = runTest {
        val server = Server(true)
        val coordinator = StartCoordinator(Settings(), server, launcher {}, direct)
        coordinator.refresh(); server.running = false; coordinator.refresh()
        assertEquals(Phase.STOPPED, coordinator.state.value.phase)
    }
    @Test fun queuedRefreshReconcilesServerAfterStartupCancellation() = runTest {
        val server = Server()
        val cleanup = CompletableDeferred<Unit>()
        val coordinator = StartCoordinator(Settings(), server, launcher {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { cleanup.await(); server.running = true } }
        }, direct)
        val startup = launch { coordinator.start() }
        runCurrent()
        startup.cancel()
        val refresh = launch { coordinator.refresh() }
        runCurrent()
        cleanup.complete(Unit)
        startup.join(); refresh.join()
        assertEquals(Phase.RUNNING, coordinator.state.value.phase)
    }
    @Test fun queuedPassiveRefreshPreservesLaunchFailureUntilRetryOrPrerequisiteChange() = runTest {
        val gate = CompletableDeferred<Unit>()
        val settings = Settings()
        val coordinator = StartCoordinator(settings, Server(), launcher {
            gate.await(); throw StartException(Failure.AUTHENTICATION)
        }, direct)
        val startup = launch { coordinator.start() }
        runCurrent()
        val refresh = launch { coordinator.refresh() }
        runCurrent(); gate.complete(Unit)
        startup.join(); refresh.join()
        assertEquals(Phase.ERROR, coordinator.state.value.phase)
        assertEquals(Failure.AUTHENTICATION, coordinator.state.value.failure)
        settings.enabled = Debugging.DISABLED
        coordinator.refresh()
        assertEquals(Phase.SETUP, coordinator.state.value.phase)
        assertNull(coordinator.state.value.failure)
    }
    @Test fun settingsDeadlineShowsRecoveryRatherThanStayingLoading() = runTest {
        val timeout = object : BoundedIo() {
            override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = withTimeout(1) { awaitCancellation() }
        }
        val coordinator = StartCoordinator(Settings(), Server(), launcher {}, timeout)
        coordinator.refresh()
        assertEquals(Phase.ERROR, coordinator.state.value.phase)
        assertEquals(Failure.TIMEOUT, coordinator.state.value.failure)
    }
    @Test fun socketStyleBlockingWorkIsCancelledAtDeadline() = runBlocking {
        val gate = CountDownLatch(1)
        var closed = false
        try {
            BoundedIo().call(100, { closed = true; gate.countDown() }) { gate.await() }
            fail("Deadline must fail")
        } catch (expected: TimeoutCancellationException) { assertTrue(closed) }
        finally { gate.countDown() }
    }
    @Test fun launchCommandRejectsUntrustedArgumentsAndDetaches() {
        val token = "a".repeat(32)
        val command = LaunchCommand.create("/data/app/example/base.apk", token, token)
        assertTrue(command.contains("setsid sh -c "))
        assertTrue(command.contains("</dev/null >>/data/local/tmp/tsuyu-start.log 2>&1"))
        assertTrue(command.contains("umask 077"))
        assertTrue(command.contains("HUP"))
        assertThrows(IllegalArgumentException::class.java) { LaunchCommand.create("/tmp/base.apk", token, token) }
        assertThrows(IllegalArgumentException::class.java) { LaunchCommand.create("/data/app/example/base.apk", "x; id", token) }
    }
}
