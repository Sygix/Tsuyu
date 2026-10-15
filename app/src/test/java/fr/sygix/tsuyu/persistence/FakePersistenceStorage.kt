/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

open class FakePersistenceStorage(enabled: Boolean = false) : PersistenceStorage {
    private var enabled = enabled
    private var selected = ""
    private var records = emptyMap<String, RestartRecord>()
    var value: StoredPersistence
        get() = StoredPersistence(enabled, records[selected] ?: RestartRecord())
        set(value) {
            enabled = value.enabled
            selected = value.record.event
            records = if (selected.isEmpty()) emptyMap() else mapOf(selected to value.record)
        }
    override suspend fun read() = value
    override suspend fun readEvent(id: String) = StoredPersistence(enabled, records[id] ?: RestartRecord())
    override suspend fun enable() { enabled = true }
    override suspend fun reserve(id: String, now: Long): RestartRecord? {
        if (!enabled) return null
        val currentEpoch = records.filterKeys { PersistenceStore.epoch(it) == PersistenceStore.epoch(id) }
        return RestartBudget.reserve(currentEpoch, id, now)?.also { record ->
            records = currentEpoch + (id to record)
            selected = id
        }
    }
    override suspend fun complete(record: RestartRecord, result: RestartOutcome) {
        if (records[record.event]?.attempts == record.attempts) records += record.event to record.copy(outcome = result)
    }
    override suspend fun recovered() {
        selected = PersistenceStore.MANUAL
        records += selected to RestartRecord(selected, outcome = RestartOutcome.RECOVERED)
    }
}
