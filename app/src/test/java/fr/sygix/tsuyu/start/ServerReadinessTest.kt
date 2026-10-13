/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerReadinessTest {
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }
    @Test fun publicationWaitsForLifecycleReadinessBeforeReconnectingUi() = runTest {
        var status = ServerStatus.PENDING
        val access = ServerReadiness(direct) { status }
        val coordinator = StartCoordinator(object : SystemSettings {
            override fun read() = true to Debugging.ENABLED
        }, access, object : ServerLauncher {
            override suspend fun launch(progress: (Phase) -> Unit) = error("Reconnection must not launch")
        }, direct)
        val observation = launch { coordinator.refresh() }
        runCurrent()
        assertFalse(observation.isCompleted)
        status = ServerStatus.READY
        advanceUntilIdle()
        assertEquals(Phase.RUNNING, coordinator.state.value.phase)
    }
    @Test fun missingAndDeadServersReturnFalseAndPendingReadinessIsBounded() = runTest {
        assertFalse(ServerReadiness(direct) { ServerStatus.ABSENT }.alive())
        var status = ServerStatus.PENDING
        val observation = async { ServerReadiness(direct) { status }.alive() }
        runCurrent(); status = ServerStatus.ABSENT
        advanceUntilIdle()
        assertFalse(observation.await())
        val result = runCatching { ServerReadiness(direct) { ServerStatus.PENDING }.alive() }
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
    }
}
