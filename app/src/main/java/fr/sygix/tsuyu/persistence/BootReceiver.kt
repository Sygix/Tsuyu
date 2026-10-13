/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "boot:${Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)}"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "update:${Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)}:${context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime}"
            else -> return
        }
        val work = OneTimeWorkRequestBuilder<RestartWorker>().setInputData(workDataOf("event" to event))
            .setInitialDelay(20, TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(RestartEvents.workName(event), ExistingWorkPolicy.KEEP, work)
    }
}
