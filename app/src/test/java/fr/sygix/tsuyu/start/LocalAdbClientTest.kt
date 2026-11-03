/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.sygix.tsuyu.adb.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAdbClientTest {
    @Test fun refreshReusesTheKeyClosesTheConnectionAndNeverLaunchesOrPrompts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keys = mutableListOf<ByteArray>()
        var unknown = false
        var publicKeys = 0
        var closes = 0
        var budget = 0L
        val io = object : BoundedIo() {
            override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T {
                budget = maxOf(budget, timeout)
                return block()
            }
        }
        val connections = AdbConnectionFactory { object : AdbConnection {
            override fun connect(port: Int) { assertEquals(5555, port) }
            override fun authenticate(key: AdbKey, awaiting: () -> Unit) {
                keys += key.publicKey()
                if (unknown) { awaiting(); publicKeys++ }
            }
            override fun shell(command: String): String = error("Refresh must not open a shell")
            override fun close() { closes++ }
        } }
        val client = LocalAdbClient(context, io, connections) { listOf(5555) }
        client.refreshKey()
        client.refreshKey()
        unknown = true
        val failure = runCatching { client.refreshKey() }.exceptionOrNull() as StartException
        assertEquals(Failure.AUTHENTICATION, failure.failure)
        assertEquals(0, publicKeys)
        assertEquals(3, closes)
        assertArrayEquals(keys[0], keys[1])
        assertArrayEquals(keys[0], keys[2])
        assertTrue(budget <= 5500)
    }
}
