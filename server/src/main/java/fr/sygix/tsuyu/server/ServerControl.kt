/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.os.Binder
import android.system.Os
import fr.sygix.tsuyu.ipc.ITsuyuServer

internal class ServerControl(private val managerUid: Int, private val ready: () -> Boolean, private val stop: () -> Unit, private val caller: () -> Int = Binder::getCallingUid, private val processUid: () -> Int = Os::getuid) : ITsuyuServer.Stub() {
    private fun enforceManager() { if (caller() != managerUid) throw SecurityException("Manager identity required") }
    override fun getUid(): Int { enforceManager(); return processUid() }
    override fun isReady(): Boolean { enforceManager(); return ready() }
    override fun exit() { enforceManager(); stop() }
}
