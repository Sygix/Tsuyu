/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.content.pm.PackageManager
import rikka.hidden.compat.PackageManagerApis
import java.security.MessageDigest

internal class ClientIdentity(private val deadline: Deadline, private val lookup: ((Int) -> AccessGroup)? = null) {
    fun resolve(uid: Int): AccessGroup = lookup?.invoke(uid) ?: deadline.call {
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
