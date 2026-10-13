/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

internal data class PackageStamp(val path: String, val updated: Long, val uid: Int)

internal class PackageWatch(private val initial: PackageStamp, private val current: () -> PackageStamp?, private val removed: () -> Unit) {
    fun reconcile(): Boolean {
        if (current() != initial) { removed(); return false }
        return true
    }
}
