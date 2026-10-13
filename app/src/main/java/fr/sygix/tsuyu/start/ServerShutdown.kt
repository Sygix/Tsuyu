/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.os.IBinder
import android.os.RemoteException
import fr.sygix.tsuyu.ipc.ITsuyuServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

class ServerShutdown(private val io: BoundedIo, private val current: () -> ITsuyuServer?) {
    suspend fun stop() = withTimeout(5000) {
        val server = current() ?: return@withTimeout
        val binder = server.asBinder()
        val died = CompletableDeferred<Unit>()
        val recipient = IBinder.DeathRecipient { died.complete(Unit) }
        if (!binder.isBinderAlive) return@withTimeout
        binder.linkToDeath(recipient, 0)
        try {
            var remoteFailure: RemoteException? = null
            try { io.call(5000) { server.exit() } }
            catch (expected: RemoteException) { remoteFailure = expected }
            try { died.await() }
            catch (failure: Exception) { remoteFailure?.let(failure::addSuppressed); throw failure }
        } finally { if (binder.isBinderAlive) binder.unlinkToDeath(recipient, 0) }
    }
}
