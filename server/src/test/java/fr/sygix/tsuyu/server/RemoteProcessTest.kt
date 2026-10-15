/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteProcessTest {
    private class Child : Process() {
        val exited = CountDownLatch(1)
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor(): Int { exited.await(); return 7 }
        override fun waitFor(timeout: Long, unit: TimeUnit) = exited.await(timeout, unit)
        override fun exitValue(): Int { if (exited.count != 0L) throw IllegalThreadStateException(); return 7 }
        override fun destroy() { exited.countDown() }
        override fun isAlive() = exited.count != 0L
    }
    @Test fun normalExitPreservesOutputAccessAndReleaseClosesTheResourceLease() {
        val child = Child()
        val released = CountDownLatch(1)
        var authorized = true
        val remote = RemoteProcess(child, { released.countDown() }) { if (!authorized) throw SecurityException() }
        try {
            remote.watch()
            assertTrue(remote.alive())
            assertFalse(remote.waitForTimeout(0, "SECONDS"))
            assertThrows(IllegalThreadStateException::class.java) { remote.exitValue() }
            authorized = false
            val operations = listOf<() -> Any?>(
                { remote.inputStream }, { remote.errorStream }, { remote.outputStream },
                { remote.alive() }, { remote.exitValue() }, { remote.waitFor() },
                { remote.waitForTimeout(0, "SECONDS") }, { remote.destroy() },
            )
            operations.forEach { operation -> assertThrows(SecurityException::class.java) { operation() } }
            child.destroy()
            authorized = true
            assertEquals(7, remote.waitFor())
            assertEquals(7, remote.exitValue())
            assertFalse(remote.alive())
            remote.close()
            assertTrue(released.await(3, TimeUnit.SECONDS))
        } finally { child.destroy(); remote.close() }
    }
}
