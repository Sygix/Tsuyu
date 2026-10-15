/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.net.LocalSocket
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionProcessTest {
    private class Supervisor : Process() {
        val stopped = CountDownLatch(1)
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor(): Int { stopped.await(); return 137 }
        override fun waitFor(timeout: Long, unit: TimeUnit) = stopped.await(timeout, unit)
        override fun exitValue() = if (isAlive) throw IllegalThreadStateException() else 137
        override fun isAlive() = stopped.count != 0L
        override fun destroy() { stopped.countDown() }
    }

    @Test fun completedTargetRetainsItsGroupLifetimeUntilExplicitRelease() {
        val supervisor = Supervisor()
        val data = ByteArrayOutputStream()
        DataOutputStream(data).apply { writeInt(ProcessProtocol.EXITED); writeInt(7) }
        val signals = mutableListOf<Int>()
        val process = SessionProcess(supervisor, LocalSocket(), DataInputStream(ByteArrayInputStream(data.toByteArray())), 124, 123) {
            signals += it
            supervisor.destroy()
        }
        assertTrue(process.waitFor(3, TimeUnit.SECONDS))
        assertEquals(7, process.exitValue())
        assertFalse(process.isAlive)
        assertTrue(supervisor.isAlive)
        OwnedProcess(process).terminate()
        assertEquals(listOf(123), signals)
        assertFalse(supervisor.isAlive)
        process.destroy()
        assertEquals(1, signals.size)
    }

    @Test fun controlProtocolRejectsMalformedLengthsAndPreservesArgumentBoundaries() {
        val bytes = ByteArrayOutputStream()
        val values = arrayOf("/system/bin/sh", "-c", "printf '%s' 'a b'", "露")
        DataOutputStream(bytes).use { ProcessProtocol.writeValues(it, values) }
        assertArrayEquals(values, ProcessProtocol.readValues(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
        val malformed = ByteArrayOutputStream()
        DataOutputStream(malformed).writeInt(Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) {
            ProcessProtocol.readText(DataInputStream(ByteArrayInputStream(malformed.toByteArray())))
        }
    }
}
