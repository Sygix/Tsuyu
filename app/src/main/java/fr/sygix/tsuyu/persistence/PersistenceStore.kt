/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.persistenceData by preferencesDataStore("persistence")

data class StoredPersistence(val enabled: Boolean = false, val record: RestartRecord = RestartRecord())

interface PersistenceStorage {
    suspend fun read(): StoredPersistence
    suspend fun readEvent(id: String): StoredPersistence
    suspend fun enable()
    suspend fun reserve(id: String, now: Long): RestartRecord?
    suspend fun complete(record: RestartRecord, result: RestartOutcome)
    suspend fun recovered()
}

class PersistenceStore(private val data: DataStore<Preferences>) : PersistenceStorage {
    constructor(context: Context) : this(context.persistenceData)
    private val enabled = booleanPreferencesKey("enabled")
    private val current = stringPreferencesKey("event")
    private val ledger = stringPreferencesKey("ledger")
    private val legacyStarted = longPreferencesKey("started")
    private val legacyAttempts = intPreferencesKey("attempts")
    private val legacyOutcome = stringPreferencesKey("outcome")

    private fun records(value: Preferences): Map<String, RestartRecord> {
        val json = JSONObject(value[ledger] ?: "{}")
        val records = json.keys().asSequence().associateWith { id ->
            val item = json.getJSONObject(id)
            RestartRecord(id, item.getLong("started"), item.getInt("attempts"), RestartOutcome.decode(item.getString("outcome")))
        }.toMutableMap()
        val event = value[current]
        if (event != null && event !in records && value[legacyOutcome] != null) {
            records[event] = RestartRecord(event, value[legacyStarted] ?: 0, value[legacyAttempts] ?: 0, RestartOutcome.decode(value[legacyOutcome]))
        }
        if (value[legacyOutcome] == RestartOutcome.RECOVERED.name) records[MANUAL] = RestartRecord(MANUAL, outcome = RestartOutcome.RECOVERED)
        return records
    }

    private fun currentEvent(value: Preferences): String =
        if (value[legacyOutcome] == RestartOutcome.RECOVERED.name) MANUAL else value[current] ?: ""

    private fun encode(records: Map<String, RestartRecord>): String = JSONObject().apply {
        records.forEach { (id, record) ->
            put(id, JSONObject().put("started", record.started).put("attempts", record.attempts).put("outcome", record.outcome.name))
        }
    }.toString()

    private fun save(value: MutablePreferences, records: Map<String, RestartRecord>, selected: String) {
        value[ledger] = encode(records)
        value[current] = selected
        value.remove(legacyStarted)
        value.remove(legacyAttempts)
        value.remove(legacyOutcome)
    }

    private fun decode(value: Preferences) = StoredPersistence(
        value[enabled] ?: false,
        records(value)[currentEvent(value)] ?: RestartRecord(),
    )

    val state = data.data.map(::decode)
    override suspend fun read() = state.first()
    override suspend fun readEvent(id: String): StoredPersistence {
        val value = data.data.first()
        return StoredPersistence(value[enabled] ?: false, records(value)[id] ?: RestartRecord())
    }
    override suspend fun enable() {
        data.edit { value ->
            save(value, records(value), currentEvent(value))
            value[enabled] = true
        }
    }
    override suspend fun recovered() {
        data.edit { value ->
            save(value, records(value) + (MANUAL to RestartRecord(MANUAL, outcome = RestartOutcome.RECOVERED)), MANUAL)
        }
    }
    override suspend fun reserve(id: String, now: Long): RestartRecord? {
        var reserved: RestartRecord? = null
        data.edit { value ->
            if (value[enabled] == true) {
                val all = records(value).filterKeys { key -> epoch(key) == epoch(id) }
                reserved = RestartBudget.reserve(all, id, now)
                reserved?.let { record -> save(value, all + (id to record), id) }
            }
        }
        return reserved
    }
    override suspend fun complete(record: RestartRecord, result: RestartOutcome) {
        data.edit { value ->
            val all = records(value)
            if (all[record.event]?.attempts == record.attempts) {
                save(value, all + (record.event to record.copy(outcome = result)), currentEvent(value))
            }
        }
    }

    companion object {
        internal const val MANUAL = "manual"
        internal fun epoch(event: String): String = event.substringAfter(':').substringBefore(':')
    }
}
