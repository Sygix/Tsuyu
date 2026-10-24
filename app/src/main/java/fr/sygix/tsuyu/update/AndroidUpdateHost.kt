/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.work.*
import fr.sygix.tsuyu.ipc.ITsuyuServer
import fr.sygix.tsuyu.persistence.PersistenceStorage
import fr.sygix.tsuyu.start.BoundedIo
import fr.sygix.tsuyu.start.Phase
import fr.sygix.tsuyu.start.StartCoordinator
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.TimeUnit

class ForegroundTracker : Application.ActivityLifecycleCallbacks {
    @Volatile private var started = 0
    val foreground get() = started > 0
    override fun onActivityStarted(activity: Activity) { started++ }
    override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

class AndroidUpdateHost(
    private val context: Context,
    private val coordinator: StartCoordinator,
    private val persistence: PersistenceStorage,
    private val server: () -> ITsuyuServer?,
    private val tracker: ForegroundTracker,
    private val io: BoundedIo = BoundedIo(),
) : UpdateHost {
    private val info get() = context.packageManager.getPackageInfo(context.packageName, 0)
    override val installed: Long get() = info.longVersionCode
    override val installedVersion: String get() = info.versionName.orEmpty()
    override fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    override fun foreground() = tracker.foreground

    override suspend fun running(): Boolean {
        coordinator.refresh()
        return coordinator.state.value.phase == Phase.RUNNING
    }

    override suspend fun prepared(): Boolean {
        if (!withTimeout(5_000) { persistence.read() }.enabled) return false
        val binder = server() ?: return false
        return io.call(16_000) { binder.persistence }.getBoolean("schedulable")
    }

    override suspend fun idle(): Boolean = io.call(5_000) { requireNotNull(server()).isIdle }

    override suspend fun install(file: File, release: Release): UpdateCause? {
        val result = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            io.call(300_000) { requireNotNull(server()).installUpdate(descriptor, release.size, release.sha256) }
        }
        return when (result.getString("status")) {
            "SUCCESS" -> null
            "DIGEST" -> UpdateCause.DIGEST
            "TIMEOUT" -> UpdateCause.TIMEOUT
            else -> UpdateCause.INSTALLER
        }
    }

    override fun schedule(auto: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!auto) {
            work.cancelUniqueWork(PERIODIC)
            work.cancelUniqueWork(RECHECK)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun recheck(delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(RECHECK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val PERIODIC = "tsuyu-update"
        const val RECHECK = "tsuyu-update-recheck"
    }
}

class UpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val updates = fr.sygix.tsuyu.TsuyuGraph.get(applicationContext).updates
        updates.load()
        updates.automatic()
        return Result.success()
    }
}
