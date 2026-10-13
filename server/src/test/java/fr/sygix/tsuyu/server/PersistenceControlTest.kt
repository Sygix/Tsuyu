/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import org.junit.Assert.*
import org.junit.Test

class PersistenceControlTest {
    private class Commands : PersistenceCommands {
        val calls = mutableListOf<List<String>>()
        var uid = "ignore"
        var pkg = "ignore"
        var unsupported = false
        var fail = false
        var timeout = false
        var value = 604800000L
        override fun execute(args: List<String>): CommandResult {
            calls += args
            if (timeout) return CommandResult(-1, "", true)
            if (fail) return CommandResult(1, "Permission denied")
            if (args[0].endsWith("settings")) {
                if (args[1] == "put") value = 0
                return CommandResult(0, "$value")
            }
            if (unsupported) return CommandResult(1, "Error: Unknown operation string: AUTO_START")
            val isUid = "--uid" in args
            if (args[1] == "set") { if (isUid) uid = "allow" else pkg = "allow"; return CommandResult(0, "") }
            return CommandResult(0, if (isUid) "Uid mode: AUTO_START: $uid" else "Uid mode: AUTO_START: $uid\nAUTO_START: $pkg")
        }
    }
    @Test fun readinessRequiresIndependentUidAndPackageReadbacks() {
        val commands = Commands()
        commands.uid = "allow"
        val control = PersistenceControl(commands)
        assertFalse(control.status().ready)
        assertTrue(control.prepare().ready)
        assertEquals(2, commands.calls.count { it[1] == "set" })
        assertEquals(604800000L, commands.value)
    }
    @Test fun unsupportedDeniedAndTimeoutAreDistinct() {
        val commands = Commands()
        val control = PersistenceControl(commands)
        commands.unsupported = true
        assertTrue(control.prepare().schedulable)
        assertFalse(control.status().ready)
        assertFalse(commands.calls.any { it[1] == "set" })
        commands.unsupported = false; commands.fail = true
        assertEquals(Preparation.DENIED, control.status().uid)
        commands.fail = false; commands.timeout = true
        assertEquals(Preparation.TIMEOUT, control.status().uid)
        assertNull(control.status().expiry)
    }
    @Test fun viewingAndPreparationNeverDisableExpiryButExplicitChoiceDoes() {
        val commands = Commands()
        val control = PersistenceControl(commands)
        control.status(); control.prepare()
        assertFalse(commands.calls.any { it[1] == "put" })
        assertEquals(0L, control.disableExpiry().expiry)
        val mutations = commands.calls.count { it[1] == "put" }
        control.disableExpiry()
        assertEquals(mutations, commands.calls.count { it[1] == "put" })
        commands.value = 123
        control.status(); control.prepare()
        assertEquals(123L, commands.value)
    }
}
