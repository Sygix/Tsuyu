/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import fr.sygix.tsuyu.TsuyuGraph

class ManagerProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        val pid = Binder.getCallingPid()
        require(uid == 2000 && extras != null)
        val identity = Binder.clearCallingIdentity()
        try {
            val graph = TsuyuGraph.get(requireNotNull(context))
            if (method != "sendServer" && method != "attachUserService") graph.broker.authenticateServer(pid)
            return when (method) {
                "sendServer" -> graph.broker.receive(uid, extras, pid).also { graph.compatibility.value = true }
                "readAccess" -> Bundle().apply { putString("access", graph.consent.read()) }
                "writeAccess" -> Bundle().apply { graph.consent.write(requireNotNull(extras.getString("access"))); putBoolean("saved", true) }
                "attachUserService" -> {
                    val server = requireNotNull(graph.broker.server.value).clientService
                    extras.putInt("childPid", pid)
                    moe.shizuku.server.IShizukuService.Stub.asInterface(server).attachUserService(requireNotNull(extras.getBinder("service")), extras)
                    Bundle().apply { putBinder("server", server) }
                }
                "requestEnded" -> Bundle().apply { check(graph.requestEnded.tryEmit(requireNotNull(extras.getString("request")))) }
                "compatibilityFailure" -> Bundle().apply { graph.compatibility.value = false }
                "compatibilitySuccess" -> Bundle().apply { graph.compatibility.value = true }
                else -> throw IllegalArgumentException("Unknown manager operation")
            }
        }
        finally { Binder.restoreCallingIdentity(identity) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
