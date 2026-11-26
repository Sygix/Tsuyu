/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import org.junit.Assert.*
import org.junit.Test

class ServerMainTest {
    @Test fun handoffSocketNameIsStablePerTokenAndNeverRevealsIt() {
        val token = "0123456789abcdef0123456789abcdef"
        val name = ServerMain.socketName(0, token)
        assertEquals(name, ServerMain.socketName(0, token))
        assertNotEquals(name, ServerMain.socketName(0, "f".repeat(32)))
        assertNotEquals(name, ServerMain.socketName(10, token))
        assertFalse(name.contains(token))
        assertTrue(name.startsWith("fr.sygix.tsuyu.server.0."))
    }
}
