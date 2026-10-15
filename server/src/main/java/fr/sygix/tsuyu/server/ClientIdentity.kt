/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import rikka.hidden.compat.PackageManagerApis
import java.security.MessageDigest

internal class ClientIdentity(private val deadline: Deadline, private val lookup: ((Int) -> AccessGroup)? = null) {
    private val verified = mutableMapOf<Int, AccessGroup>()
    private var observing = false
    @Synchronized fun invalidate() { verified.clear() }
    fun observe(context: Context, changed: () -> Unit) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { invalidate(); changed() }
        }, filter)
        synchronized(this) { observing = true }
    }
    @Synchronized fun resolve(uid: Int): AccessGroup {
        if (observing) verified[uid]?.let { return it }
        return resolveInstalled(uid).also { if (observing) verified[uid] = it }
    }
    private fun resolveInstalled(uid: Int): AccessGroup = lookup?.invoke(uid) ?: deadline.call(Deadline.Use.IDENTITY) {
        val packages = requireNotNull(PackageManagerApis.getPackagesForUid(uid))
        AccessGroup(uid, packages.associateWith { name ->
            val info = requireNotNull(PackageManagerApis.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES.toLong(), uid / 100000))
            check(info.applicationInfo?.uid == uid)
            requireNotNull(info.signingInfo).apkContentsSigners.map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
            }.sorted().joinToString(":")
        })
    }
}
