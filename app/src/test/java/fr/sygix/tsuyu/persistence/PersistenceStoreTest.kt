/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun realStorageAndFakePreserveIndependentBudgetsAcrossManualRecovery() = runTest {
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temporary.newFile("state.preferences_pb") })
        for (store in listOf(PersistenceStore(data), FakePersistenceStorage())) {
            store.enable()
            val boot = requireNotNull(store.reserve("boot:2", 1000))
            store.complete(boot, RestartOutcome.ENDPOINT)
            val update = requireNotNull(store.reserve("update:2:99", 2000))
            store.complete(update, RestartOutcome.RUNNING)
            store.recovered()
            assertEquals(RestartOutcome.RECOVERED, store.read().record.outcome)
            assertEquals(RestartOutcome.ENDPOINT, store.readEvent("boot:2").record.outcome)
            assertEquals(RestartOutcome.RUNNING, store.readEvent("update:2:99").record.outcome)
            val retry = requireNotNull(store.reserve("boot:2", 3000))
            assertEquals(2, retry.attempts)
            store.complete(boot, RestartOutcome.RUNNING)
            assertEquals(RestartOutcome.ATTEMPT, store.readEvent("boot:2").record.outcome)
            store.complete(retry, RestartOutcome.EXHAUSTED)
            assertNull(store.reserve("boot:2", 4000))
            assertEquals(1, store.reserve("boot:3", 100)?.attempts)
        }
    }

    @Test fun legacyDuplicatedRecordsMigrateWithoutLosingRecoveryOrBudgets() = runTest {
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { temporary.newFile("legacy.preferences_pb") })
        data.edit { value ->
            value[booleanPreferencesKey("enabled")] = true
            value[stringPreferencesKey("event")] = "boot:2"
            value[longPreferencesKey("started")] = 1000
            value[intPreferencesKey("attempts")] = 3
            value[stringPreferencesKey("outcome")] = "RECOVERED"
            value[stringPreferencesKey("ledger")] = """{"boot:2":{"started":1000,"attempts":3,"outcome":"EXHAUSTED"}}"""
        }
        val store = PersistenceStore(data)
        assertEquals(RestartOutcome.RECOVERED, store.read().record.outcome)
        assertEquals(RestartOutcome.EXHAUSTED, store.readEvent("boot:2").record.outcome)
        store.enable()
        assertEquals(setOf("enabled", "event", "ledger"), data.data.first().asMap().keys.map { it.name }.toSet())
        assertEquals(RestartOutcome.RECOVERED, store.read().record.outcome)
        assertNull(store.reserve("boot:2", 2000))
    }
}
