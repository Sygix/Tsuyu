/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.updateData by preferencesDataStore("updates")

data class Pending(val code: Long, val version: String, val path: String, val verifiedAt: Long, val boot: Int)

data class UpdateSaved(
    val auto: Boolean = false,
    val prereleases: Boolean = false,
    val checkedAt: Long? = null,
    val result: String? = null,
    val retryAt: Long = 0,
    val releases: List<Release> = emptyList(),
    val pending: Pending? = null,
    val target: Long? = null,
    val targetVersion: String? = null,
    val noticeShown: Boolean = true,
)

interface UpdateStorage {
    suspend fun read(): UpdateSaved
    suspend fun update(transform: (UpdateSaved) -> UpdateSaved): UpdateSaved
}

class UpdateStore(private val data: DataStore<Preferences>) : UpdateStorage {
    constructor(context: Context) : this(context.updateData)
    private val key = stringPreferencesKey("state")

    override suspend fun read(): UpdateSaved = decode(data.data.first()[key])

    override suspend fun update(transform: (UpdateSaved) -> UpdateSaved): UpdateSaved {
        var next = UpdateSaved()
        data.edit { value -> next = transform(decode(value[key])); value[key] = encode(next) }
        return next
    }

    companion object {
        fun encode(saved: UpdateSaved): String = JSONObject().apply {
            put("auto", saved.auto); put("prereleases", saved.prereleases)
            saved.checkedAt?.let { put("checkedAt", it) }
            saved.result?.let { put("result", it) }
            put("retryAt", saved.retryAt)
            put("releases", Releases.encode(saved.releases))
            saved.pending?.let { put("pending", JSONObject().put("code", it.code).put("version", it.version).put("path", it.path).put("verifiedAt", it.verifiedAt).put("boot", it.boot)) }
            saved.target?.let { put("target", it) }
            saved.targetVersion?.let { put("targetVersion", it) }
            put("noticeShown", saved.noticeShown)
        }.toString()

        fun decode(value: String?): UpdateSaved {
            if (value == null) return UpdateSaved()
            val json = JSONObject(value)
            return UpdateSaved(
                auto = json.optBoolean("auto"),
                prereleases = json.optBoolean("prereleases"),
                checkedAt = if (json.has("checkedAt")) json.getLong("checkedAt") else null,
                result = json.optString("result").takeIf { it.isNotEmpty() },
                retryAt = json.optLong("retryAt"),
                releases = Releases.parse(json.optString("releases", "[]")),
                pending = json.optJSONObject("pending")?.let { Pending(it.getLong("code"), it.getString("version"), it.getString("path"), it.getLong("verifiedAt"), it.getInt("boot")) },
                target = if (json.has("target")) json.getLong("target") else null,
                targetVersion = json.optString("targetVersion").takeIf { it.isNotEmpty() },
                noticeShown = json.optBoolean("noticeShown", true),
            )
        }
    }
}
