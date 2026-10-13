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
    suspend fun enable()
    suspend fun reserve(id: String, now: Long): RestartRecord?
    suspend fun complete(record: RestartRecord, result: String)
}
class PersistenceStore(private val context: Context) : PersistenceStorage {
    private val enabled = booleanPreferencesKey("enabled")
    private val event = stringPreferencesKey("event")
    private val started = longPreferencesKey("started")
    private val attempts = intPreferencesKey("attempts")
    private val outcome = stringPreferencesKey("outcome")
    private fun decode(value: Preferences) = StoredPersistence(value[enabled] ?: false, RestartRecord(value[event] ?: "", value[started] ?: 0, value[attempts] ?: 0, value[outcome] ?: "NONE"))
    val state = context.persistenceData.data.map(::decode)
    override suspend fun read() = state.first()
    override suspend fun enable() { context.persistenceData.edit { it[enabled] = true } }
    override suspend fun reserve(id: String, now: Long): RestartRecord? {
        var reserved: RestartRecord? = null
        context.persistenceData.edit { value ->
            if (value[enabled] == true) {
                reserved = RestartBudget.reserve(decode(value).record, id, now)
                reserved?.let { record -> value[event] = record.event; value[started] = record.started; value[attempts] = record.attempts; value[outcome] = record.outcome }
            }
        }
        return reserved
    }
    override suspend fun complete(record: RestartRecord, result: String) {
        context.persistenceData.edit { value -> if (value[event] == record.event && value[attempts] == record.attempts) value[outcome] = result }
    }
}
