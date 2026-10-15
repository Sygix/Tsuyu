/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageReplacementReceiverTest {
    @Test fun replacementHasImmediateCleanupIndependentOfPersistenceAndWorkerDelay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val handlers = context.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(context.packageName), 0)
        assertTrue(handlers.any { it.activityInfo.name == PackageReplacementReceiver::class.java.name })
        val stopped = CountDownLatch(1)
        org.robolectric.Shadows.shadowOf(context as android.app.Application).clearRegisteredReceivers()
        val receiver = PackageReplacementReceiver { stopped.countDown() }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_MY_PACKAGE_REPLACED))
        try {
            context.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
            ShadowLooper.idleMainLooper()
            assertTrue(stopped.await(3, TimeUnit.SECONDS))
        } finally { context.unregisterReceiver(receiver) }
    }
}
