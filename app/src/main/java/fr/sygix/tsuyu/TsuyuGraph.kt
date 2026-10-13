/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu

import android.content.Context
import fr.sygix.tsuyu.start.*
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

class TsuyuGraph private constructor(context: Context) {
    val broker = ServerBroker(context)
    val settings = AndroidSettings(context)
    private val io = BoundedIo()
    val coordinator = StartCoordinator(settings, object : ServerAccess {
        override fun alive(): Boolean = broker.server.value?.let { it.asBinder().isBinderAlive && it.uid == 2000 && it.isReady } == true
    }, LocalAdbLauncher(context, broker, io), io) { failure -> android.util.Log.e("Tsuyu", "Startup operation failed", failure) }
    suspend fun stopReplacedServer() = withTimeout(5000) {
        val server = broker.server.value ?: return@withTimeout
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
    companion object {
        @Volatile private var graph: TsuyuGraph? = null
        fun get(context: Context): TsuyuGraph = graph ?: synchronized(this) {
            graph ?: TsuyuGraph(context.applicationContext).also { graph = it }
        }
    }
}
