/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.os.Binder
import android.system.Os
import fr.sygix.tsuyu.ipc.ITsuyuServer

internal class ServerControl(private val managerUid: Int, private val ready: () -> Boolean, private val stop: () -> Unit, private val caller: () -> Int = Binder::getCallingUid, private val processUid: () -> Int = Os::getuid, private val clients: ClientService? = null, private val persistence: PersistenceControl? = null) : ITsuyuServer.Stub() {
    private fun enforceManager() { if (caller() != managerUid) throw SecurityException("Manager identity required") }
    override fun getUid(): Int { enforceManager(); return processUid() }
    override fun isReady(): Boolean { enforceManager(); return ready() }
    override fun exit() { enforceManager(); stop() }
    override fun getClientService(): android.os.IBinder { enforceManager(); return requireNotNull(clients) }
    override fun getRequest(token: String): android.os.Bundle { enforceManager(); return requireNotNull(clients).request(token) }
    override fun confirm(token: String, allowed: Boolean) { enforceManager(); requireNotNull(clients).confirm(token, allowed) }
    override fun getAccess(): android.os.Bundle { enforceManager(); return requireNotNull(clients).accessList() }
    override fun revoke(uid: Int) { enforceManager(); requireNotNull(clients).revoke(uid) }
    override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
        try { return super.onTransact(code, data, reply, flags) }
        catch (failure: android.os.RemoteException) { throw failure }
        catch (failure: RuntimeException) { throw failure }
        catch (failure: Exception) { throw IllegalStateException("Manager operation failed; check the service and retry", failure) }
    }
    private fun snapshot(operation: () -> PersistenceSnapshot): android.os.Bundle {
        enforceManager()
        val identity = Binder.clearCallingIdentity()
        try {
            val result = operation()
            return android.os.Bundle().apply { putString("uid", result.uid.name); putString("package", result.pkg.name); if (result.expiry != null) putLong("expiry", result.expiry); putBoolean("ready", result.ready); putBoolean("schedulable", result.schedulable) }
        } finally { Binder.restoreCallingIdentity(identity) }
    }
    override fun getPersistence() = snapshot { requireNotNull(persistence).status() }
    override fun preparePersistence() = snapshot { requireNotNull(persistence).prepare() }
    override fun disableKeyExpiry() = snapshot { requireNotNull(persistence).disableExpiry() }
}
