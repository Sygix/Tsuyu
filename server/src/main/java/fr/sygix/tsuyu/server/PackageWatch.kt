/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

internal data class PackageStamp(val path: String, val updated: Long, val uid: Int)

internal class PackageWatch(private val initial: PackageStamp, private val current: () -> PackageStamp?, private val removed: () -> Unit, launchedPath: String = initial.path, launchedUid: Int = initial.uid) {
    init { check(initial.path == launchedPath && initial.uid == launchedUid) { "Installed package changed during launch" } }
    fun reconcile(): Boolean {
        if (current() != initial) { removed(); return false }
        return true
    }
}
