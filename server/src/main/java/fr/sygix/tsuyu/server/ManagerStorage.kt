/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

internal class ManagerStorage(private val bridge: ProviderBridge, private val deadline: Deadline) : AccessStorage {
    override fun read(): List<AccessDecision> {
        val result = deadline.call(Deadline.Use.STORAGE) { bridge.call("fr.sygix.tsuyu.manager", "readAccess", Bundle()) }
        val array = JSONArray(requireNotNull(result?.getString("access")))
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val members = item.getJSONObject("members")
            AccessDecision(AccessGroup(item.getInt("uid"), members.keys().asSequence().associateWith { members.getString(it) }), item.getBoolean("allowed"))
        }
    }
    override fun write(decisions: List<AccessDecision>) {
        val array = JSONArray()
        decisions.forEach { array.put(JSONObject().put("uid", it.group.uid).put("members", JSONObject(it.group.members)).put("allowed", it.allowed)) }
        check(deadline.call(Deadline.Use.STORAGE) { bridge.call("fr.sygix.tsuyu.manager", "writeAccess", Bundle().apply { putString("access", array.toString()) }) }?.getBoolean("saved") == true)
    }
}
