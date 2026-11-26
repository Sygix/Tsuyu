/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.distribution

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File
import org.json.JSONObject

internal val updateCorruption = ReplaceFileCorruptionHandler { emptyPreferences() }
internal val Context.updateData by preferencesDataStore("updates", corruptionHandler = updateCorruption)

class StoreCleanup(
    private val data: DataStore<Preferences>,
    private val cacheDir: File,
    private val cancelWork: suspend (String) -> Unit,
) {
    suspend fun run(): Result<Unit> = try {
        withTimeout(TIMEOUT_MS) {
            if (data.data.first()[DONE] != true) {
                WORK_NAMES.forEach { cancelWork(it) }
                removeManagedFiles()
                data.edit { value ->
                    val observed = value[STATE]?.let { state -> runCatching { JSONObject(state).optLong("observed", -1) }.getOrDefault(-1) } ?: -1
                    value.clear()
                    if (observed >= 0) value[STATE] = JSONObject().put("observed", observed).toString()
                    value[DONE] = true
                }
            }
        }
        Result.success(Unit)
    } catch (expired: TimeoutCancellationException) {
        Result.failure(expired)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { Result.failure(failure) }

    private fun removeManagedFiles() {
        File(cacheDir, CACHE_DIRECTORY).listFiles()?.forEach { file ->
            if (file.isFile) check(file.delete()) { "Update cache file not removed" }
        }
    }

    companion object {
        val WORK_NAMES = listOf("tsuyu-update", "tsuyu-update-recheck")
        const val CACHE_DIRECTORY = "update"
        private val DONE = booleanPreferencesKey("store_cleanup_done")
        private val STATE = stringPreferencesKey("state")
        private const val TIMEOUT_MS = 10_000L
    }
}
