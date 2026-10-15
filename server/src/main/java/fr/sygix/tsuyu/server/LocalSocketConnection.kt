/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.net.LocalSocket
import android.net.LocalSocketAddress

internal object LocalSocketConnection {
    fun connect(socket: LocalSocket, name: String) {
        Deadline().use { deadline ->
            deadline.call { socket.connect(LocalSocketAddress(name)) }
        }
    }
}
