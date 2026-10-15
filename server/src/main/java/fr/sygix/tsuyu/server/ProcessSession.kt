/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.net.LocalServerSocket
import android.net.LocalSocket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class ProcessSession(private val apk: String) {
    fun launch(command: Array<out String>, environment: Array<out String>? = null, directory: String? = null, discardOutput: Boolean = false): Process {
        val name = "fr.sygix.tsuyu.process.${UUID.randomUUID().toString().replace("-", "")}"
        val listener = LocalServerSocket(name)
        val accepted = CompletableFuture<LocalSocket>()
        Thread {
            try { accepted.complete(listener.accept()) }
            catch (failure: Exception) { accepted.completeExceptionally(failure) }
        }.apply { isDaemon = true; start() }
        var supervisor: Process? = null
        var connection: LocalSocket? = null
        var session = false
        try {
            supervisor = ProcessBuilder(
                "/system/bin/app_process", "/system/bin", "--nice-name=fr.sygix.tsuyu:process",
                "fr.sygix.tsuyu.server.ProcessSupervisor", name,
            ).apply {
                environment()["CLASSPATH"] = apk
                if (discardOutput) {
                    redirectOutput(File("/dev/null"))
                    redirectError(File("/dev/null"))
                }
            }.start()
            connection = accepted.get(ProcessProtocol.DEADLINE_MS.toLong(), TimeUnit.MILLISECONDS)
            val supervisorPid = OwnedProcess(supervisor).pid
            check(connection.peerCredentials.uid == 2000 && connection.peerCredentials.pid == supervisorPid)
            connection.soTimeout = ProcessProtocol.DEADLINE_MS
            val input = DataInputStream(connection.inputStream)
            check(input.readInt() == ProcessProtocol.SESSION)
            session = true
            val output = DataOutputStream(connection.outputStream)
            ProcessProtocol.writeValues(output, command)
            ProcessProtocol.writeValues(output, environment)
            ProcessProtocol.writeText(output, directory ?: "")
            output.flush()
            when (input.readInt()) {
                ProcessProtocol.FAILED -> throw IOException(ProcessProtocol.readText(input))
                ProcessProtocol.EXECUTED -> {
                    val targetPid = input.readInt().also { require(it > 0) }
                    connection.soTimeout = 0
                    return SessionProcess(supervisor, connection, input, targetPid, supervisorPid)
                }
                else -> error("Invalid process execution acknowledgement")
            }
        } catch (failure: Exception) {
            try {
                supervisor?.let {
                    if (session) SessionProcess.killGroup(OwnedProcess(it).pid)
                    else it.destroyForcibly()
                    check(it.waitFor(5, TimeUnit.SECONDS)) { "Process startup cleanup unverified" }
                }
            } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            connection?.close()
            throw IOException("Cannot start owned process", failure)
        } finally {
            listener.close()
            if (connection == null) accepted.thenAccept { it.close() }
        }
    }
}
