/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import fr.sygix.tsuyu.persistence.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestartRunnerTest {
    private val direct = object : BoundedIo() { override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block() }
    private class Store : PersistenceStorage {
        var value = StoredPersistence(true)
        override suspend fun read() = value
        override suspend fun enable() { value = value.copy(enabled = true) }
        override suspend fun reserve(id: String, now: Long): RestartRecord? = RestartBudget.reserve(value.record, id, now)?.also { value = value.copy(record = it) }
        override suspend fun complete(record: RestartRecord, result: String) { if (value.record.event == record.event && value.record.attempts == record.attempts) value = value.copy(record = record.copy(outcome = result)) }
    }
    @Test fun delayedEndpointRetriesThenEstablishesLiveServerOnce() = runTest {
        val store = Store()
        var running = false
        var launches = 0
        val settings = object : SystemSettings { override fun read() = true to Debugging.ENABLED }
        val coordinator = StartCoordinator(settings, object : ServerAccess { override suspend fun alive() = running }, object : ServerLauncher {
            override suspend fun launch(progress: (Phase) -> Unit) { launches++; if (launches == 1) throw StartException(Failure.ENDPOINT); running = true }
        }, direct)
        val runner = RestartRunner(store, coordinator, settings, { testScheduler.currentTime }, io = direct)
        assertEquals(RestartResult.RETRY, runner.run("boot:1"))
        advanceTimeBy(30000)
        assertEquals(RestartResult.SUCCESS, runner.run("boot:1"))
        assertEquals("RUNNING", store.value.record.outcome)
        assertEquals(RestartResult.SUCCESS, runner.run("boot:1"))
        assertEquals(2, launches)
    }
    @Test fun exhaustionAndDisabledUnknownPrerequisitesAreFinite() = runTest {
        val store = Store()
        var debugging = Debugging.ENABLED
        var launches = 0
        val settings = object : SystemSettings { override fun read() = true to debugging }
        val coordinator = StartCoordinator(settings, object : ServerAccess { override suspend fun alive() = false }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) { launches++; throw StartException(Failure.ENDPOINT) } }, direct)
        val runner = RestartRunner(store, coordinator, settings, { testScheduler.currentTime }, io = direct)
        repeat(2) { assertEquals(RestartResult.RETRY, runner.run("boot:1")); advanceTimeBy(30000) }
        assertEquals(RestartResult.FAILURE, runner.run("boot:1"))
        assertEquals("EXHAUSTED", store.value.record.outcome)
        assertEquals(RestartResult.FAILURE, runner.run("boot:1"))
        assertEquals(3, launches)
        debugging = Debugging.DISABLED
        assertEquals(RestartResult.FAILURE, runner.run("boot:2"))
        assertEquals("SETUP", store.value.record.outcome)
        debugging = Debugging.UNKNOWN
        assertEquals(RestartResult.FAILURE, runner.run("boot:3"))
        assertEquals("UNKNOWN", store.value.record.outcome)
        assertEquals(3, launches)
    }
    @Test fun simultaneousManualStartAndBootConvergeOnOneHandshake() = runTest {
        val store = Store()
        var running = false
        var launches = 0
        val gate = CompletableDeferred<Unit>()
        val settings = object : SystemSettings { override fun read() = true to Debugging.ENABLED }
        val coordinator = StartCoordinator(settings, object : ServerAccess { override suspend fun alive() = running }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) { launches++; gate.await(); running = true } }, direct)
        val manual = launch { coordinator.start() }
        runCurrent()
        val boot = async { RestartRunner(store, coordinator, settings, { testScheduler.currentTime }, io = direct).run("boot:1") }
        runCurrent(); gate.complete(Unit)
        manual.join()
        assertEquals(RestartResult.SUCCESS, boot.await())
        assertEquals(1, launches)
    }
    @Test fun authenticationFailureNeverConsumesThreeConsentAttempts() = runTest {
        val store = Store()
        var launches = 0
        val settings = object : SystemSettings { override fun read() = true to Debugging.ENABLED }
        val coordinator = StartCoordinator(settings, object : ServerAccess { override suspend fun alive() = false }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) { launches++; throw StartException(Failure.AUTHENTICATION) } }, direct)
        val runner = RestartRunner(store, coordinator, settings, { testScheduler.currentTime }, io = direct)
        assertEquals(RestartResult.FAILURE, runner.run("boot:1"))
        assertEquals(RestartResult.FAILURE, runner.run("boot:1"))
        assertEquals(1, launches)
    }
}
