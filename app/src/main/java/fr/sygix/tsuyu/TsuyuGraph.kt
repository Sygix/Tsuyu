/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu

import android.content.Context
import fr.sygix.tsuyu.start.*

class TsuyuGraph private constructor(context: Context) {
    val consent = fr.sygix.tsuyu.access.ConsentStorage(context)
    val persistence = fr.sygix.tsuyu.persistence.PersistenceStore(context)
    val requestEnded = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 16)
    val compatibility = kotlinx.coroutines.flow.MutableStateFlow(true)
    val broker = ServerBroker(context)
    val settings = AndroidSettings(context)
    private val io = BoundedIo()
    val coordinator = StartCoordinator(settings, ServerReadiness(io) {
        val server = broker.server.value
        when {
            server == null || !server.asBinder().isBinderAlive -> ServerStatus.ABSENT
            server.uid != 2000 -> throw StartException(Failure.LAUNCH)
            server.isReady -> ServerStatus.READY
            else -> ServerStatus.PENDING
        }
    }, LocalAdbLauncher(context, broker, io), io) { failure -> android.util.Log.e("Tsuyu", "Startup operation failed", failure) }
    suspend fun stopReplacedServer() = ServerShutdown(io) { broker.server.value }.stop()
    val persistenceController = fr.sygix.tsuyu.persistence.PersistenceController(persistence, coordinator, { broker.server.value }, io)
    private val tracker = fr.sygix.tsuyu.update.ForegroundTracker().also { (context.applicationContext as android.app.Application).registerActivityLifecycleCallbacks(it) }
    val updateHost = fr.sygix.tsuyu.update.AndroidUpdateHost(context, coordinator, persistence, { broker.server.value }, tracker, io)
    val updates = fr.sygix.tsuyu.update.UpdateController(
        fr.sygix.tsuyu.update.UpdateStore(context),
        fr.sygix.tsuyu.update.GitHubReleases("Tsuyu/${updateHost.installedVersion}"),
        fr.sygix.tsuyu.update.AndroidUpdateFiles(context),
        updateHost,
    )
    companion object {
        @Volatile private var graph: TsuyuGraph? = null
        fun get(context: Context): TsuyuGraph = graph ?: synchronized(this) {
            graph ?: TsuyuGraph(context.applicationContext).also { graph = it }
        }
    }
}
