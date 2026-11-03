/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.Context
import fr.sygix.tsuyu.adb.AdbConnection
import fr.sygix.tsuyu.adb.AdbConnectionFactory
import fr.sygix.tsuyu.adb.AdbKeyStore
import fr.sygix.tsuyu.adb.SocketAdbConnection
import fr.sygix.tsuyu.adb.UnsupportedAdb
import kotlinx.coroutines.CancellationException
import java.io.File
import java.net.SocketTimeoutException

class LocalAdbClient(
    private val context: Context,
    private val io: BoundedIo,
    private val connections: AdbConnectionFactory = AdbConnectionFactory { SocketAdbConnection() },
    private val ports: () -> List<Int> = { AndroidAdbPorts.discover() },
) {
    suspend fun <T> use(authenticationTimeout: Long = 125_000, awaiting: () -> Unit, block: suspend (AdbConnection) -> T): T {
        val key = try { io.call(5000) { AdbKeyStore(File(context.noBackupFilesDir, "adb-key")).load() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw StartException(Failure.KEY, failure) }
        val candidates = io.call(5000) { ports().filter { it in 1..65535 }.distinct() }
        for (port in candidates) {
            val connection = connections.create()
            try {
                try { io.call(5500, { connection.close() }) { connection.connect(port) } }
                catch (failure: java.io.IOException) { continue }
                try {
                    io.call(authenticationTimeout, { connection.close() }) { connection.authenticate(key, awaiting) }
                } catch (failure: UnsupportedAdb) { throw StartException(Failure.UNSUPPORTED, failure) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: SocketTimeoutException) { throw StartException(Failure.TIMEOUT, failure) }
                catch (failure: StartException) { throw failure }
                catch (failure: Exception) { throw StartException(Failure.AUTHENTICATION, failure) }
                return block(connection)
            } finally { connection.close() }
        }
        throw StartException(Failure.ENDPOINT)
    }

    suspend fun refreshKey() = use(authenticationTimeout = 5000, awaiting = { throw StartException(Failure.AUTHENTICATION) }) { Unit }
}
