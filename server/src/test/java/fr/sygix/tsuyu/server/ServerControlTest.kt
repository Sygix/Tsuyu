/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerControlTest {
    @Test fun everyManagerMethodRejectsOtherUidsEvenWithTheBinder() {
        var caller = 10100
        var ready = false
        var stopped = false
        val server = ServerControl(10100, { ready }, { stopped = true }, { caller }, { 2000 })
        assertEquals(2000, server.uid)
        assertFalse(server.isReady)
        ready = true
        assertTrue(server.isReady)
        caller = 10101
        assertThrows(SecurityException::class.java) { server.uid }
        assertThrows(SecurityException::class.java) { server.isReady }
        assertThrows(SecurityException::class.java) { server.exit() }
        assertThrows(SecurityException::class.java) { server.persistence }
        assertThrows(SecurityException::class.java) { server.preparePersistence() }
        assertThrows(SecurityException::class.java) { server.disableKeyExpiry() }
        assertFalse(stopped)
        caller = 10100
        server.exit()
        assertTrue(stopped)
    }
}
