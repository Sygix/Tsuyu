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

internal class ClientDelivery(
    private val binder: IBinder,
    private val userId: Int,
    private val deadline: Deadline,
    private val reportFailure: () -> Unit,
    private val reportSuccess: () -> Unit = {},
    private val enumerate: ((Int) -> List<String>)? = null,
    private val send: ((String, Int) -> Unit)? = null,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val reportClient: (String, Boolean) -> Unit = { _, _ -> },
) : AutoCloseable {
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val delivered = ConcurrentHashMap.newKeySet<Int>()
    private val owners = ConcurrentHashMap<Int, Int>()
    private val retryAfter = ConcurrentHashMap<Int, Long>()
    private val events = Executors.newScheduledThreadPool(2)
    private val uidObserver = object : UidObserverAdapter() {
        override fun onUidActive(uid: Int) { activeUid(uid) }
        override fun onUidCachedChanged(uid: Int, cached: Boolean) { if (!cached) activeUid(uid) }
    }
    private val processObserver = object : ProcessObserverAdapter() {
        override fun onForegroundActivitiesChanged(pid: Int, uid: Int, foreground: Boolean) { if (foreground) processStarted(pid, uid) }
        override fun onProcessStateChanged(pid: Int, uid: Int, state: Int) { processStarted(pid, uid) }
        override fun onProcessStarted(pid: Int, processUid: Int, packageUid: Int, packageName: String?, processName: String?) { processStarted(pid, packageUid) }
        override fun onProcessDied(pid: Int, uid: Int) { processDied(pid) }
    }
    fun processDied(pid: Int) { delivered.remove(pid); owners.remove(pid) }
    override fun close() {
        events.shutdown()
        if (!events.awaitTermination(6, TimeUnit.SECONDS)) events.shutdownNow()
    }
    fun processStarted(pid: Int, uid: Int) {
        if (uid / PER_USER_RANGE != userId || clock() < (retryAfter[uid] ?: 0)) return
        owners[pid] = uid
        if (delivered.add(pid)) deliverUid(uid)
    }
    private fun retryUid(uid: Int) {
        retryAfter[uid] = clock() + RETRY_COOLDOWN_MS
        owners.entries.filter { it.value == uid }.forEach { delivered.remove(it.key) }
    }
    private fun activeUid(uid: Int) {
        if (uid / PER_USER_RANGE != userId) return
        events.execute {
            try {
                val processes = deadline.call {
                    val manager = Class.forName("android.app.ActivityManager").getDeclaredMethod("getService").invoke(null)
                    @Suppress("UNCHECKED_CAST")
                    Class.forName("android.app.IActivityManager").getMethod("getRunningAppProcesses")
                        .invoke(manager) as? List<android.app.ActivityManager.RunningAppProcessInfo>
                }
                processes.orEmpty().filter { it.uid == uid }.forEach { processStarted(it.pid, uid) }
            } catch (failure: Exception) {
                android.util.Log.e("TsuyuServer", "Client process lookup failed", failure)
                reportFailure()
            }
        }
    }
    fun start() {
        deadline.call { ActivityManagerApis.registerUidObserver(uidObserver, UID_OBSERVER_ACTIVE or UID_OBSERVER_CACHED, -1, null) }
        deadline.call { ActivityManagerApis.registerProcessObserver(processObserver) }
        val installed = deadline.call { requireNotNull(PackageManagerApis.getInstalledPackages(PackageManager.GET_PERMISSIONS.toLong(), userId)).list }
        installed.filter { it.requestedPermissions?.contains("moe.shizuku.manager.permission.API_V23") == true }.forEach { deliver(it.packageName, userId) }
    }
    private fun deliverUid(uid: Int) {
        if (uid / 100000 != userId) return
        events.execute {
            try {
                if (enumerate != null) {
                    enumerate.invoke(uid).forEach { deliver(it, uid / PER_USER_RANGE, uid = uid) }
                    return@execute
                }
                val packages = deadline.call { PackageManagerApis.getPackagesForUid(uid) } ?: return@execute
                packages.forEach { name ->
                    val info = deadline.call { PackageManagerApis.getPackageInfo(name, PackageManager.GET_PERMISSIONS.toLong(), uid / 100000) }
                    if (info?.requestedPermissions?.contains("moe.shizuku.manager.permission.API_V23") == true) deliver(name, uid / 100000, uid = uid)
                }
            } catch (failure: Exception) { retryUid(uid); android.util.Log.e("TsuyuServer", "Client enumeration failed", failure); reportFailure() }
        }
    }
    private fun deliver(name: String, user: Int, attempt: Int = 0, uid: Int? = null) {
        val key = "$user:$name"
        if (attempt == 0 && !pending.add(key)) return
        events.execute {
            try {
                deadline.call(Deadline.Use.DELIVERY) {
                    if (send != null) send.invoke(name, user)
                    else ProviderBridge(user).call("$name.shizuku", "sendBinder", Bundle().apply {
                        putParcelable("moe.shizuku.privileged.api.intent.extra.BINDER", BinderContainer(binder))
                    })
                }
                pending.remove(key)
                reportClient(name, true)
                reportSuccess()
            } catch (missing: ProviderUnavailable) {
                pending.remove(key)
                reportClient(name, false)
            } catch (failure: Exception) {
                android.util.Log.e("TsuyuServer", "Client binder delivery failed", failure)
                if (attempt < 2) events.schedule({ deliver(name, user, attempt + 1, uid) }, 1, TimeUnit.SECONDS)
                else {
                    pending.remove(key)
                    if (uid != null) retryUid(uid)
                    reportClient(name, false)
                }
                reportFailure()
            }
        }
    }
    companion object {
        private const val PER_USER_RANGE = 100_000
        private const val UID_OBSERVER_ACTIVE = 2
        private const val UID_OBSERVER_CACHED = 16
        private const val RETRY_COOLDOWN_MS = 5000
    }
}
