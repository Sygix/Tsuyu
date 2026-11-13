/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.languageData by preferencesDataStore("language")

data class StoredLanguage(val selected: AppLanguage? = null, val migrated: Boolean = false)

interface LanguageStorage {
    suspend fun read(): StoredLanguage
    suspend fun select(language: AppLanguage?)
    suspend fun migrated()
}

class LanguageStore(private val data: DataStore<Preferences>) : LanguageStorage {
    constructor(context: Context) : this(context.applicationContext.languageData)
    private val selected = stringPreferencesKey("selected")
    private val migrated = booleanPreferencesKey("migrated")

    override suspend fun read(): StoredLanguage {
        val value = data.data.first()
        val tag = value[selected]
        val language = tag?.let { saved -> requireNotNull(AppLanguage.entries.firstOrNull { it.tag == saved }) }
        return StoredLanguage(language, value[migrated] ?: false)
    }

    override suspend fun select(language: AppLanguage?) {
        data.edit { value ->
            if (language == null) value.remove(selected) else value[selected] = language.tag
        }
    }

    override suspend fun migrated() { data.edit { it[migrated] = true } }
}
