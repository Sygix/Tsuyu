/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.system.Os
import android.system.OsConstants
import java.util.concurrent.TimeUnit

internal class OwnedProcess(private val process: java.lang.Process, private val identity: () -> Int = { process.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(process) }, private val kill: (Int) -> Unit = { Os.kill(it, OsConstants.SIGKILL) }) {
    val pid get() = identity().also { check(it > 0) }
    fun terminate() {
        synchronized(process) {
            if (process.isAlive) {
                try { kill(-pid) }
                catch (failure: android.system.ErrnoException) { if (failure.errno != OsConstants.ESRCH) throw failure }
            }
        }
        check(process.waitFor(5, TimeUnit.SECONDS)) { "Owned process termination unverified" }
    }
}
