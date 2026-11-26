/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.distribution

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import fr.sygix.tsuyu.TsuyuGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

object StoreDistribution : Distribution {
    override val offersUpdates = false
    override val notice = MutableStateFlow<String?>(null).asStateFlow()
    override val updateBlocked = flowOf(false)
    override fun homeCreated() = Unit
    override fun homeStopped() = Unit
    override fun openUpdates(activity: Activity) = Unit
}

internal fun createDistribution(context: Context, graph: TsuyuGraph): Distribution = StoreDistribution

internal fun onApplicationStart(application: Application) {
    val cleanup = StoreCleanup(application.updateData, application.cacheDir) { name ->
        runInterruptible { WorkManager.getInstance(application).cancelUniqueWork(name).result.get() }
    }
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        cleanup.run().onFailure { Log.e("Tsuyu", "Updater cleanup failed; it will be retried at the next start", it) }
    }
}
