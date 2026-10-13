/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.os.IBinder
import android.os.RemoteException
import fr.sygix.tsuyu.ipc.ITsuyuServer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerShutdownTest {
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }
    private class Server(private val die: Boolean) : ITsuyuServer.Stub() {
        var recipient: IBinder.DeathRecipient? = null
        var exits = 0
        var unlinks = 0
        override fun getUid() = 2000
        override fun isReady() = true
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { this.recipient = recipient }
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean { unlinks++; return true }
        override fun exit() { exits++; if (die) { recipient?.binderDied(); throw RemoteException("Server exited") } }
    }
    @Test fun preservedManagerTerminatesOldServerAndConfirmsBinderDeath() = runTest {
        val server = Server(true)
        ServerShutdown(direct) { server }.stop()
        assertEquals(1, server.exits)
        assertEquals(1, server.unlinks)
    }
    @Test fun missingServerIsHarmlessButMissingDeathTimesOutAndReleasesObserver() = runTest {
        ServerShutdown(direct) { null }.stop()
        val server = Server(false)
        assertTrue(runCatching { ServerShutdown(direct) { server }.stop() }.exceptionOrNull() is TimeoutCancellationException)
        assertEquals(1, server.exits)
        assertEquals(1, server.unlinks)
    }
}
