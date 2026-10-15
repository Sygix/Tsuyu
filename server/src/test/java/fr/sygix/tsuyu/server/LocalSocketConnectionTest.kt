/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalSocketConnectionTest {
    @Test fun connectionUsesTheImplementedAndroidOverload() {
        var connected: String? = null
        val socket = object : LocalSocket() {
            override fun connect(address: LocalSocketAddress) { connected = address.name }
            override fun connect(address: LocalSocketAddress, timeout: Int) {
                throw UnsupportedOperationException("Android does not implement this overload")
            }
        }
        socket.use { LocalSocketConnection.connect(it, "fixture") }
        assertEquals("fixture", connected)
    }
}
