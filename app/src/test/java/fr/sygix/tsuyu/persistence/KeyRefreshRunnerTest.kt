/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import fr.sygix.tsuyu.start.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyRefreshRunnerTest {
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }
    private val settings = object : SystemSettings { override fun read() = true to Debugging.ENABLED }

    @Test fun refreshesOnlyAfterEnablementAndRecordsRecoveryFromAnExpiredKey() = runTest {
        val store = FakePersistenceStorage()
        var refreshes = 0
        val runner = KeyRefreshRunner(store, settings, { refreshes++ }, direct)
        assertEquals(KeyRefreshResult.SUCCESS, runner.run())
        assertEquals(0, refreshes)
        store.enable()
        store.keyRefreshed(KeyRefreshOutcome.AUTHORIZATION_REQUIRED)
        assertEquals(KeyRefreshResult.SUCCESS, runner.run())
        assertEquals(1, refreshes)
        assertEquals(KeyRefreshOutcome.CURRENT, store.read().keyRefresh)
    }

    @Test fun disabledDebuggingIsNeverReenabledAndUnknownSettingsAreActionable() = runTest {
        val store = FakePersistenceStorage(true)
        var debugging = Debugging.DISABLED
        val runner = KeyRefreshRunner(store, object : SystemSettings {
            override fun read() = true to debugging
        }, { fail("Must not connect") }, direct)
        assertEquals(KeyRefreshResult.SUCCESS, runner.run())
        assertEquals(KeyRefreshOutcome.NONE, store.read().keyRefresh)
        debugging = Debugging.UNKNOWN
        assertEquals(KeyRefreshResult.RETRY, runner.run())
        assertEquals(KeyRefreshOutcome.FAILED, store.read().keyRefresh)
    }

    @Test fun expiredAuthorizationDoesNotRetryConsentAndTransientFailuresCanRecover() = runTest {
        val store = FakePersistenceStorage(true)
        val failures = mutableListOf<Exception>()
        for ((failure, expected, outcome) in listOf(
            Triple(Failure.AUTHENTICATION, KeyRefreshResult.FAILURE, KeyRefreshOutcome.AUTHORIZATION_REQUIRED),
            Triple(Failure.ENDPOINT, KeyRefreshResult.RETRY, KeyRefreshOutcome.FAILED),
            Triple(Failure.KEY, KeyRefreshResult.RETRY, KeyRefreshOutcome.FAILED),
        )) {
            assertEquals(expected, KeyRefreshRunner(store, settings, { throw StartException(failure) }, direct, failures::add).run())
            assertEquals(outcome, store.read().keyRefresh)
        }
        assertEquals(3, failures.size)
    }

    @Test fun stalledRefreshIsBoundedAndExternalCancellationDoesNotRecordFailure() = runTest {
        val store = FakePersistenceStorage(true)
        val runner = KeyRefreshRunner(store, settings, { awaitCancellation() }, direct)
        assertEquals(KeyRefreshResult.RETRY, runner.run())
        assertEquals(KeyRefreshOutcome.FAILED, store.read().keyRefresh)
        store.keyRefreshed(KeyRefreshOutcome.CURRENT)
        val work = launch { runner.run() }
        runCurrent()
        work.cancel()
        work.join()
        assertEquals(KeyRefreshOutcome.CURRENT, store.read().keyRefresh)
    }
}
