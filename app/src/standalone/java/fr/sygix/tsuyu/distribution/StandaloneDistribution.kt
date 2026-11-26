/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.distribution

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import fr.sygix.tsuyu.TsuyuGraph
import fr.sygix.tsuyu.start.BoundedIo
import fr.sygix.tsuyu.update.AndroidUpdateFiles
import fr.sygix.tsuyu.update.AndroidUpdateHost
import fr.sygix.tsuyu.update.ForegroundTracker
import fr.sygix.tsuyu.update.GitHubReleases
import fr.sygix.tsuyu.update.UpdateActivity
import fr.sygix.tsuyu.update.UpdateController
import fr.sygix.tsuyu.update.UpdateStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StandaloneDistribution(context: Context, private val graph: TsuyuGraph) : Distribution {
    private val tracker = ForegroundTracker().also { (context.applicationContext as Application).registerActivityLifecycleCallbacks(it) }
    val host = AndroidUpdateHost(context, graph.coordinator, graph.persistence, { graph.broker.server.value }, tracker, BoundedIo())
    val updates = UpdateController(UpdateStore(context), GitHubReleases("Tsuyu/${host.installedVersion}"), AndroidUpdateFiles(context), host)
    override val offersUpdates = true
    override val notice = updates.notice
    override val updateBlocked = updates.state.map { it.blocked }
    override fun homeCreated() {
        graph.scope.launch {
            updates.load()
            if (!updates.state.value.auto) updates.check(false)
        }
    }
    override fun homeStopped() { graph.scope.launch { updates.consumeNotice() } }
    override fun openUpdates(activity: Activity) { activity.startActivity(Intent(activity, UpdateActivity::class.java)) }
}

internal fun createDistribution(context: Context, graph: TsuyuGraph): Distribution = StandaloneDistribution(context, graph)

internal fun onApplicationStart(application: Application) = Unit

val TsuyuGraph.updates: UpdateController get() = (distribution as StandaloneDistribution).updates
val TsuyuGraph.updateHost: AndroidUpdateHost get() = (distribution as StandaloneDistribution).host
