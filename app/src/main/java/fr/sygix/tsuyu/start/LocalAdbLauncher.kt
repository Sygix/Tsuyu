/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.Context
import fr.sygix.tsuyu.adb.AdbKeyStore
import fr.sygix.tsuyu.adb.AdbConnectionFactory
import fr.sygix.tsuyu.adb.SocketAdbConnection
import fr.sygix.tsuyu.adb.UnsupportedAdb
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import java.io.File
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class LocalAdbLauncher(private val context: Context, private val broker: ServerBroker, private val io: BoundedIo, private val connections: AdbConnectionFactory = AdbConnectionFactory { SocketAdbConnection() }, private val ports: () -> List<Int> = { AndroidAdbPorts.discover() }) : ServerLauncher {
    override suspend fun launch(progress: (Phase) -> Unit) {
        val key = try { io.call(5000) { AdbKeyStore(File(context.noBackupFilesDir, "adb-key")).load() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw StartException(Failure.KEY, failure) }
        val identity = broker.prepare()
        val candidates = io.call(5000) { ports().filter { it in 1..65535 }.distinct() }
        var connected = false
        for (port in candidates) {
            val connection = connections.create()
            try {
                try { io.call(5500, { connection.close() }) { connection.connect(port) } }
                catch (failure: java.io.IOException) { continue }
                connected = true
                try {
                    io.call(125_000, { connection.close() }) { connection.authenticate(key) { progress(Phase.AUTHORIZING) } }
                } catch (failure: UnsupportedAdb) { throw StartException(Failure.UNSUPPORTED, failure) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: SocketTimeoutException) { throw StartException(Failure.TIMEOUT, failure) }
                catch (failure: Exception) { throw StartException(Failure.AUTHENTICATION, failure) }
                progress(Phase.LAUNCHING)
                try {
                    val path = io.call(5000) { context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir }
                    val command = LaunchCommand.create(path, identity.first, identity.second)
                    val output = io.call(30_000, { connection.close() }) { connection.shell(command) }
                    if (output.lineSequence().none { it == "TSUYU_LAUNCHED" }) throw StartException(Failure.LAUNCH)
                    withTimeout(30_000) {
                        val service = broker.server.filterNotNull().first()
                        while (!io.call(5000) { service.isReady }) delay(100)
                    }
                    return
                } catch (failure: TimeoutCancellationException) { throw StartException(Failure.LAUNCH, failure) }
                catch (failure: SocketTimeoutException) { throw StartException(Failure.LAUNCH, failure) }
            } finally { connection.close() }
        }
        throw StartException(if (connected) Failure.AUTHENTICATION else Failure.ENDPOINT)
    }
}

internal object AndroidAdbPorts {
    fun discover(): List<Int> {
        val ports = mutableListOf<Int>()
        for (name in listOf("service.adb.tcp.port", "persist.adb.tcp.port")) {
            val process = ProcessBuilder("/system/bin/getprop", name).start()
            try {
                check(process.waitFor(1, TimeUnit.SECONDS)) { "Property lookup timed out" }
                val port = process.inputStream.bufferedReader().readText().trim().toIntOrNull()
                if (port != null && port in 1..65535) ports += port
            } finally { process.destroy(); process.inputStream.close(); process.errorStream.close(); process.outputStream.close() }
        }
        return (ports + 5555).distinct()
    }
}
