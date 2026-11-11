/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import fr.sygix.tsuyu.TsuyuGraph
import fr.sygix.tsuyu.language.LocalizedActivity
import fr.sygix.tsuyu.start.TestServerStub
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessLanguageGateTest {
    @After fun forgetGraph() {
        TsuyuGraph::class.java.getDeclaredField("graph").apply { isAccessible = true }.set(null, null)
    }

    @Test fun leavingTheLanguageGateOfAPermissionRequestDeniesIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(android.Manifest.permission.HIDE_OVERLAY_WINDOWS)
        val broker = TsuyuGraph.get(context).broker
        val decisions = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Boolean>>()
        val decided = CountDownLatch(1)
        val (token, generation) = broker.prepare()
        broker.receive(2000, Bundle().apply {
            putString("token", token)
            putString("generation", generation)
            putBinder("server", object : TestServerStub() {
                override fun getUid() = 2000
                override fun isReady() = true
                override fun exit() = Unit
                override fun getRequest(token: String) = Bundle().apply { putInt("uid", 10001); putStringArrayList("packages", arrayListOf("client")) }
                override fun confirm(token: String, allowed: Boolean) { decisions += token to allowed; decided.countDown() }
            })
        }, 4242)
        val controller = Robolectric.buildActivity(AccessActivity::class.java, Intent(context, AccessActivity::class.java).putExtra("request", "pending")).setup()
        shadowOf(Looper.getMainLooper()).idle()
        LocalizedActivity::class.java.getDeclaredMethod("onLanguageGateBack").apply { isAccessible = true }.invoke(controller.get())
        assertTrue(decided.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("pending" to false), decisions.toList())
    }
}
