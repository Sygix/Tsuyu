/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.Context
import android.os.Binder
import android.os.Bundle
import fr.sygix.tsuyu.ipc.ITsuyuServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ServerBroker(context: Context) {
    private val preferences = context.getSharedPreferences("launch", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow<ITsuyuServer?>(null)
    val server = mutable.asStateFlow()
    private val client = Binder()
    @Synchronized fun prepare(): Pair<String, String> {
        val token = preferences.getString("token", null) ?: nonce()
        val generation = nonce()
        check(preferences.edit().putString("token", token).putString("generation", generation).commit())
        return token to generation
    }
    @Synchronized fun receive(uid: Int, extra: Bundle): Bundle {
        require(uid == 2000) { "Shell provider caller required" }
        require(extra.getString("token") == preferences.getString("token", null) && extra.getString("token") != null)
        require(extra.getString("generation") == preferences.getString("generation", null))
        val binder = requireNotNull(extra.getBinder("server"))
        val service = ITsuyuServer.Stub.asInterface(binder)
        require(service.uid == 2000) { "Invalid server uid" }
        binder.linkToDeath({ synchronized(this) { if (mutable.value?.asBinder() == binder) mutable.value = null } }, 0)
        mutable.value = service
        return Bundle().apply { putBinder("client", client) }
    }
    companion object { private fun nonce() = UUID.randomUUID().toString().replace("-", "") }
}
