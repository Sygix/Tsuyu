/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.sygix.tsuyu.TsuyuGraph
import fr.sygix.tsuyu.start.Phase
import fr.sygix.tsuyu.start.LocalAdbClient
import fr.sygix.tsuyu.start.BoundedIo
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class KeyRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = TsuyuGraph.get(applicationContext)
        if (graph.coordinator.state.value.phase in listOf(Phase.CONNECTING, Phase.AUTHORIZING, Phase.LAUNCHING)) return Result.success()
        val io = BoundedIo()
        val adb = LocalAdbClient(applicationContext, io)
        return try {
            when (KeyRefreshRunner(graph.persistence, graph.settings, adb::refreshKey, io, report = {
                Log.e("Tsuyu", "ADB key refresh failed", it)
            }).run()) {
                KeyRefreshResult.SUCCESS -> Result.success()
                KeyRefreshResult.FAILURE -> Result.failure()
                KeyRefreshResult.RETRY -> if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            Log.e("Tsuyu", "ADB key refresh result unavailable", failure)
            Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<KeyRefreshWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(12, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("tsuyu-key-refresh", ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}
