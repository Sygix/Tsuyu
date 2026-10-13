/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

private val Context.consent by preferencesDataStore("consent")
class ConsentStorage(private val context: Context) {
    private val key = stringPreferencesKey("access")
    fun read(): String = runBlocking { withTimeout(4000) { context.consent.data.first()[key] ?: "[]" } }
    fun write(value: String) {
        require(value.length <= 262144)
        JSONArray(value)
        runBlocking { withTimeout(4000) { context.consent.edit { it[key] = value } } }
    }
}
