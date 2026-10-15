/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.net.LocalSocket
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileDescriptor
import kotlin.system.exitProcess

object ProcessSupervisor {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            check(Os.getuid() == 2000)
            HiddenApiAccess.enable()
            require(args.size == 1 && args[0].matches(Regex("fr\\.sygix\\.tsuyu\\.process\\.[a-f0-9]{32}")))
            Os.setsid()
            LocalSocket().use { socket ->
                LocalSocketConnection.connect(socket, args[0])
                check(socket.peerCredentials.uid == 2000)
                socket.soTimeout = ProcessProtocol.DEADLINE_MS
                val input = DataInputStream(socket.inputStream)
                val output = DataOutputStream(socket.outputStream)
                output.writeInt(ProcessProtocol.SESSION)
                output.flush()
                val command = requireNotNull(ProcessProtocol.readValues(input))
                require(command.isNotEmpty())
                val environment = ProcessProtocol.readValues(input)
                val directory = ProcessProtocol.readText(input)
                val process = try {
                    ProcessBuilder(command.toList()).apply {
                        inheritIO()
                        if (directory.isNotEmpty()) directory(File(directory))
                        if (environment != null) {
                            environment().clear()
                            environment.forEach { value ->
                                val separator = value.indexOf('=')
                                require(separator > 0)
                                environment()[value.substring(0, separator)] = value.substring(separator + 1)
                            }
                        }
                    }.start()
                } catch (failure: Exception) {
                    output.writeInt(ProcessProtocol.FAILED)
                    ProcessProtocol.writeText(output, failure.message ?: "Process execution failed")
                    output.flush()
                    return@use
                }
                Os.close(FileDescriptor.`in`)
                Os.close(FileDescriptor.out)
                Os.close(FileDescriptor.err)
                output.writeInt(ProcessProtocol.EXECUTED)
                output.writeInt(OwnedProcess(process).pid)
                output.flush()
                socket.soTimeout = 0
                Thread {
                    try {
                        val exit = process.waitFor()
                        output.writeInt(ProcessProtocol.EXITED)
                        output.writeInt(exit)
                        output.flush()
                    } catch (failure: Exception) {
                        Log.e("TsuyuServer", "Process exit notification failed", failure)
                    }
                }.apply { isDaemon = true; start() }
                check(input.read() == -1) { "Unexpected process control data" }
            }
        } catch (failure: Exception) {
            Log.e("TsuyuServer", "Process supervision failed", failure)
        } finally {
            try { Os.kill(-Os.getpid(), OsConstants.SIGKILL) }
            finally { exitProcess(1) }
        }
    }
}
