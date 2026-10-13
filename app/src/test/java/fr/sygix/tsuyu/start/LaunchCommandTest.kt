/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LaunchCommandTest {
    @Test fun launchShellWaitsForServerReadinessBeforeCompleting() {
        val directory = Files.createTempDirectory("tsuyu-launch-test").toFile()
        val started = directory.resolve("started")
        val proceed = directory.resolve("proceed")
        val finish = directory.resolve("finish")
        val executable = directory.resolve("server")
        val executor = Executors.newSingleThreadExecutor()
        var pid: String? = null
        var shell: Process? = null
        try {
            for (fifo in listOf(started, proceed, finish)) {
                assertEquals(0, ProcessBuilder("mkfifo", fifo.path).start().waitFor())
            }
            executable.writeText("#!/bin/sh\necho $$ > '${started.path}'\nread go < '${proceed.path}'\necho TSUYU_READY\nread done < '${finish.path}'\n")
            assertTrue(executable.setExecutable(true))
            val token = "a".repeat(32)
            val command = LaunchCommand.create("/data/app/fixture/base.apk", token, token)
                .replace("/system/bin/app_process", executable.path)
                .replace("/data/local/tmp/tsuyu-start.log", directory.resolve("launch.log").path)
            shell = ProcessBuilder("sh", "-c", command).start()
            pid = executor.submit<String> { started.bufferedReader().use { it.readLine() } }.get(5, TimeUnit.SECONDS)
            assertFalse("The launch shell must not complete before the server is ready", shell.waitFor(200, TimeUnit.MILLISECONDS))
            executor.submit { proceed.bufferedWriter().use { it.write("ready\n") } }.get(5, TimeUnit.SECONDS)
            assertTrue(shell.waitFor(5, TimeUnit.SECONDS))
            assertEquals(0, shell.exitValue())
            assertEquals("TSUYU_LAUNCHED", shell.inputStream.bufferedReader().readText().trim())
            assertTrue("The detached server must outlive launch-shell completion", ProcessHandle.of(pid.toLong()).orElseThrow().isAlive)
            executor.submit { finish.bufferedWriter().use { it.write("done\n") } }.get(5, TimeUnit.SECONDS)
        } finally {
            pid?.let { ProcessBuilder("kill", "-TERM", it).start().waitFor() }
            shell?.destroyForcibly()
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }
}
