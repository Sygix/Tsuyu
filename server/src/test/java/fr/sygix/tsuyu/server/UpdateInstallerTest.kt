/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class UpdateInstallerTest {
    private class FakeProcess(output: String, private val finishes: Boolean = true) : Process() {
        val written = ByteArrayOutputStream()
        private val input = ByteArrayInputStream(output.toByteArray())
        override fun getOutputStream() = written
        override fun getInputStream() = input
        override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))
        override fun waitFor() = 0
        override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit) = finishes
        override fun exitValue() = 0
        override fun destroy() = Unit
    }

    private val apk = ByteArray(1000) { it.toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private val commands = mutableListOf<List<String>>()
    private val processes = mutableListOf<FakeProcess>()

    private fun installer(vararg outputs: Pair<String, Boolean>) = UpdateInstaller(0, { command ->
        commands += command
        val (output, finishes) = outputs.getOrElse(commands.size - 1) { "Success" to true }
        FakeProcess(output, finishes).also { processes += it }
    }, 1_000)

    @Test fun streamsTheVerifiedFileIntoATsuyuOnlySessionThenCommits() {
        val outcome = installer("Success: created install session [42]" to true, "Success: streamed 1000 bytes" to true, "Success" to true)
            .install(ByteArrayInputStream(apk), apk.size.toLong(), sha)
        assertEquals(InstallStatus.SUCCESS, outcome.status)
        assertEquals(listOf("/system/bin/pm", "install-create", "-r", "--user", "0", "--pkg", "fr.sygix.tsuyu", "-S", "1000"), commands[0])
        assertEquals(listOf("/system/bin/pm", "install-write", "-S", "1000", "42", "base.apk", "-"), commands[1])
        assertEquals(listOf("/system/bin/pm", "install-commit", "42"), commands[2])
        assertEquals(3, commands.size)
        assertArrayEquals(apk, processes[1].written.toByteArray())
    }

    @Test fun digestMismatchAbandonsTheSessionWithoutCommitting() {
        val outcome = installer("Success: created install session [7]" to true, "Success" to true)
            .install(ByteArrayInputStream(apk), apk.size.toLong(), "0".repeat(64))
        assertEquals(InstallStatus.DIGEST, outcome.status)
        assertEquals(listOf("/system/bin/pm", "install-abandon", "7"), commands.last())
        assertTrue(commands.none { "install-commit" in it })
    }

    @Test fun installerRefusalReportsItsStatus() {
        val outcome = installer("Success: created install session [9]" to true, "Success" to true, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures]" to true)
            .install(ByteArrayInputStream(apk), apk.size.toLong(), sha)
        assertEquals(InstallOutcome(InstallStatus.INSTALLER, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"), outcome)
        assertEquals(listOf("/system/bin/pm", "install-abandon", "9"), commands.last())
    }

    @Test fun stalledWriteTimesOutAndAbandons() {
        val outcome = installer("Success: created install session [5]" to true, "" to false)
            .install(ByteArrayInputStream(apk), apk.size.toLong(), sha)
        assertEquals(InstallStatus.TIMEOUT, outcome.status)
        assertEquals(listOf("/system/bin/pm", "install-abandon", "5"), commands.last())
    }

    @Test fun commitTimeoutAbandonsTheStillOpenSession() {
        val outcome = installer("Success: created install session [11]" to true, "Success" to true, "" to false)
            .install(ByteArrayInputStream(apk), apk.size.toLong(), sha)
        assertEquals(InstallStatus.TIMEOUT, outcome.status)
        assertEquals(listOf("/system/bin/pm", "install-abandon", "11"), commands.last())
    }

    @Test fun shortFileNeverCommitsAndInputIsBounded() {
        val outcome = installer("Success: created install session [3]" to true, "Success" to true)
            .install(ByteArrayInputStream(apk.copyOf(10)), apk.size.toLong(), sha)
        assertNotEquals(InstallStatus.SUCCESS, outcome.status)
        assertTrue(commands.none { "install-commit" in it })
        assertThrows(IllegalArgumentException::class.java) { installer().install(ByteArrayInputStream(apk), 0, sha) }
        assertThrows(IllegalArgumentException::class.java) { installer().install(ByteArrayInputStream(apk), 1, "x") }
    }
}
