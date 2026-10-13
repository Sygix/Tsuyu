/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import fr.sygix.tsuyu.ipc.ITsuyuServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerBrokerTest {
    @Test fun forgedUidStaleGenerationAndNonShellServerAreRejected() {
        val broker = ServerBroker(ApplicationProvider.getApplicationContext())
        val (token, generation) = broker.prepare()
        fun extra(uid: Int = 2000, gen: String = generation) = Bundle().apply {
            putString("token", token); putString("generation", gen)
            putBinder("server", object : ITsuyuServer.Stub() {
                override fun getUid() = uid
                override fun isReady() = true
                override fun exit() = Unit
            })
        }
        assertThrows(IllegalArgumentException::class.java) { broker.receive(12345, extra()) }
        assertThrows(IllegalArgumentException::class.java) { broker.receive(2000, extra(gen = "stale")) }
        assertThrows(IllegalArgumentException::class.java) { broker.receive(2000, extra(uid = 0)) }
        assertNull(broker.server.value)
        assertNotNull(broker.receive(2000, extra()).getBinder("client"))
        assertEquals(2000, broker.server.value?.uid)
    }
}
