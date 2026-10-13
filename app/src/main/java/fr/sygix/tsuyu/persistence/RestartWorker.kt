/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.sygix.tsuyu.TsuyuGraph

class RestartWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = TsuyuGraph.get(applicationContext)
        val event = inputData.getString("event") ?: return Result.failure()
        val boot = android.provider.Settings.Global.getInt(applicationContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
        val update = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).lastUpdateTime
        if (!RestartEvents.current(event, boot, update)) return Result.success()
        return when (RestartRunner(graph.persistence, graph.coordinator, graph.settings, SystemClock::elapsedRealtime, graph::stopReplacedServer).run(event)) {
            RestartResult.SUCCESS -> Result.success()
            RestartResult.FAILURE -> Result.failure()
            RestartResult.RETRY -> Result.retry()
        }
    }
}
