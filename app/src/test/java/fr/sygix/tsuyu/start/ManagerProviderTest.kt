/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import fr.sygix.tsuyu.TsuyuGraph
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManagerProviderTest {
    @Test fun authenticatedStatusRecoversButShellChildrenCannotForgeIt() {
        val provider = Robolectric.buildContentProvider(ManagerProvider::class.java).create().get()
        val graph = TsuyuGraph.get(ApplicationProvider.getApplicationContext())
        val (token, generation) = graph.broker.prepare()
        ShadowBinder.setCallingUid(2000)
        ShadowBinder.setCallingPid(123)
        provider.call("sendServer", null, Bundle().apply {
            putString("token", token)
            putString("generation", generation)
            putBinder("server", object : TestServerStub() {
                override fun getUid() = 2000
                override fun isReady() = true
                override fun exit() = Unit
            })
        })
        provider.call("compatibilityFailure", null, Bundle())
        assertFalse(graph.compatibility.value)
        provider.call("compatibilitySuccess", null, Bundle())
        assertTrue(graph.compatibility.value)
        ShadowBinder.setCallingPid(124)
        for (method in listOf("writeAccess", "readAccess", "requestEnded", "compatibilityFailure")) {
            assertThrows(IllegalStateException::class.java) { provider.call(method, null, Bundle()) }
        }
        assertTrue(graph.compatibility.value)
        ShadowBinder.setCallingUid(10001)
        assertThrows(IllegalArgumentException::class.java) { provider.call("sendServer", null, Bundle()) }
    }
}
