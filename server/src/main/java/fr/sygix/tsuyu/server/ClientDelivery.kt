/*
 * Copyright (C) RikkaW and contributors
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: Apache-2.0
 * Modified by Sygix from Shizuku/server/src/main/java/rikka/shizuku/server/BinderSender.java.
 */

package fr.sygix.tsuyu.server

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import moe.shizuku.api.BinderContainer
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.adapter.ProcessObserverAdapter
import rikka.hidden.compat.adapter.UidObserverAdapter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ClientDelivery(private val binder: IBinder, private val userId: Int, private val deadline: Deadline) {
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val events = Executors.newScheduledThreadPool(2)
    private val uidObserver = object : UidObserverAdapter() {
        override fun onUidActive(uid: Int) { deliverUid(uid) }
        override fun onUidCachedChanged(uid: Int, cached: Boolean) { if (!cached) deliverUid(uid) }
        override fun onUidIdle(uid: Int, disabled: Boolean) { deliverUid(uid) }
    }
    private val processObserver = object : ProcessObserverAdapter() {
        override fun onForegroundActivitiesChanged(pid: Int, uid: Int, foreground: Boolean) { if (foreground) deliverUid(uid) }
        override fun onProcessStateChanged(pid: Int, uid: Int, state: Int) { deliverUid(uid) }
        override fun onProcessStarted(pid: Int, processUid: Int, packageUid: Int, packageName: String?, processName: String?) { deliverUid(packageUid) }
    }
    fun start() {
        deadline.call { ActivityManagerApis.registerUidObserver(uidObserver, 2 or 4 or 8 or 16, -1, null) }
        deadline.call { ActivityManagerApis.registerProcessObserver(processObserver) }
        val installed = deadline.call { requireNotNull(PackageManagerApis.getInstalledPackages(PackageManager.GET_PERMISSIONS.toLong(), userId)).list }
        installed.filter { it.requestedPermissions?.contains("moe.shizuku.manager.permission.API_V23") == true }.forEach { deliver(it.packageName, userId) }
    }
    private fun deliverUid(uid: Int) {
        if (uid / 100000 != userId) return
        events.execute {
            try {
                val packages = deadline.call { PackageManagerApis.getPackagesForUid(uid) } ?: return@execute
                packages.forEach { name ->
                    val info = deadline.call { PackageManagerApis.getPackageInfo(name, PackageManager.GET_PERMISSIONS.toLong(), uid / 100000) }
                    if (info?.requestedPermissions?.contains("moe.shizuku.manager.permission.API_V23") == true) deliver(name, uid / 100000)
                }
            } catch (failure: Exception) { android.util.Log.e("TsuyuServer", "Client enumeration failed", failure) }
        }
    }
    private fun deliver(name: String, user: Int, attempt: Int = 0) {
        val key = "$user:$name"
        if (attempt == 0 && !pending.add(key)) return
        events.execute {
            try {
                deadline.call { ProviderBridge(user).call("$name.shizuku", "sendBinder", Bundle().apply { putParcelable("moe.shizuku.privileged.api.intent.extra.BINDER", BinderContainer(binder)) }) }
                pending.remove(key)
            } catch (failure: Exception) {
                android.util.Log.e("TsuyuServer", "Client binder delivery failed", failure)
                if (attempt < 2) events.schedule({ deliver(name, user, attempt + 1) }, 1, TimeUnit.SECONDS) else pending.remove(key)
            }
        }
    }
}
