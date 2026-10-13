/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.persistenceData by preferencesDataStore("persistence")
data class StoredPersistence(val enabled: Boolean = false, val record: RestartRecord = RestartRecord())
interface PersistenceStorage {
    suspend fun read(): StoredPersistence
    suspend fun readEvent(id: String): StoredPersistence = read().let { if (it.record.event == id) it else it.copy(record = RestartRecord()) }
    suspend fun enable()
    suspend fun reserve(id: String, now: Long): RestartRecord?
    suspend fun complete(record: RestartRecord, result: String)
    suspend fun recovered() { complete(read().record, "RECOVERED") }
}
class PersistenceStore(private val context: Context) : PersistenceStorage {
    private val enabled = booleanPreferencesKey("enabled")
    private val event = stringPreferencesKey("event")
    private val started = longPreferencesKey("started")
    private val attempts = intPreferencesKey("attempts")
    private val outcome = stringPreferencesKey("outcome")
    private val ledger = stringPreferencesKey("ledger")
    private fun records(value: Preferences): Map<String, RestartRecord> {
        val objectValue = org.json.JSONObject(value[ledger] ?: "{}")
        return objectValue.keys().asSequence().associateWith { id ->
            val item = objectValue.getJSONObject(id)
            RestartRecord(id, item.getLong("started"), item.getInt("attempts"), item.getString("outcome"))
        }
    }
    private fun encode(records: Map<String, RestartRecord>): String = org.json.JSONObject().apply {
        records.forEach { (id, record) -> put(id, org.json.JSONObject().put("started", record.started).put("attempts", record.attempts).put("outcome", record.outcome)) }
    }.toString()
    private fun decode(value: Preferences) = StoredPersistence(value[enabled] ?: false, RestartRecord(value[event] ?: "", value[started] ?: 0, value[attempts] ?: 0, value[outcome] ?: "NONE"))
    val state = context.persistenceData.data.map(::decode)
    override suspend fun read() = state.first()
    override suspend fun readEvent(id: String): StoredPersistence {
        val value = context.persistenceData.data.first()
        return StoredPersistence(value[enabled] ?: false, records(value)[id] ?: RestartRecord())
    }
    override suspend fun enable() { context.persistenceData.edit { it[enabled] = true } }
    override suspend fun recovered() { context.persistenceData.edit { it[outcome] = "RECOVERED" } }
    override suspend fun reserve(id: String, now: Long): RestartRecord? {
        var reserved: RestartRecord? = null
        context.persistenceData.edit { value ->
            if (value[enabled] == true) {
                val all = records(value).filterKeys { key -> key.substringAfter(':').substringBefore(':') == id.substringAfter(':').substringBefore(':') }
                reserved = RestartLedger.reserve(all, id, now)
                reserved?.let { record -> value[ledger] = encode(all + (id to record)); value[event] = record.event; value[started] = record.started; value[attempts] = record.attempts; value[outcome] = record.outcome }
            }
        }
        return reserved
    }
    override suspend fun complete(record: RestartRecord, result: String) {
        context.persistenceData.edit { value ->
            val all = records(value)
            if (all[record.event]?.attempts == record.attempts) {
                value[ledger] = encode(all + (record.event to record.copy(outcome = result)))
                if (value[event] == record.event && value[attempts] == record.attempts) value[outcome] = result
            }
        }
    }
}
