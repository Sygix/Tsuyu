/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class OwnedProcessTest {
    private class Child : java.lang.Process() {
        var running = true
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor() = if (running) throw IllegalThreadStateException() else 0
        override fun waitFor(timeout: Long, unit: TimeUnit) = !running
        override fun exitValue() = waitFor()
        override fun destroy() = Unit
    }
    @Test fun anOwnedChildIgnoringNormalTerminationStillStops() {
        val process = Child()
        var signalled: Int? = null
        OwnedProcess(process, { 123 }, { signalled = it; process.running = false }).terminate()
        assertFalse(process.running)
        assertEquals(123, signalled)
    }
    @Test fun exitedChildIsNeverSignalledAndUnconfirmedDeathIsNotSuccess() {
        val exited = Child().apply { running = false }
        var calls = 0
        OwnedProcess(exited, { 123 }, { calls++ }).terminate()
        assertEquals(0, calls)
        assertThrows(IllegalStateException::class.java) { OwnedProcess(Child(), { 123 }, {}).terminate() }
    }
}
