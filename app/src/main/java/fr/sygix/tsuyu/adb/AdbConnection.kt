/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.adb

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket

interface AdbConnection : Closeable {
    fun connect(port: Int)
    fun authenticate(key: AdbKey, awaiting: () -> Unit)
    fun shell(command: String): String
}
fun interface AdbConnectionFactory { fun create(): AdbConnection }

class SocketAdbConnection : AdbConnection {
    private val socket = Socket()
    private var session: AdbSession? = null
    override fun connect(port: Int) = socket.connect(InetSocketAddress("127.0.0.1", port), 5000)
    override fun authenticate(key: AdbKey, awaiting: () -> Unit) {
        socket.soTimeout = 120_000
        session = AdbSession(socket.getInputStream(), socket.getOutputStream(), key).also { it.authenticate(awaiting) }
    }
    override fun shell(command: String): String {
        socket.soTimeout = 30_000
        return requireNotNull(session).shell(command)
    }
    override fun close() = socket.close()
}
