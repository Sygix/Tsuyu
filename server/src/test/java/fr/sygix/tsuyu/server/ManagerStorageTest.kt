/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManagerStorageTest {
    @Test fun sharedIdentityDecisionsRoundTripOnlyAfterAcknowledgedWrites() {
        var json = "[]"
        var saved = true
        val storage = ManagerStorage { method, data ->
            when (method) {
                "readAccess" -> Bundle().apply { putString("access", json) }
                "writeAccess" -> Bundle().apply { if (saved) json = requireNotNull(data.getString("access")); putBoolean("saved", saved) }
                else -> error("Unexpected operation")
            }
        }
        val decisions = listOf(AccessDecision(AccessGroup(110001, mapOf("first" to "a", "second" to "b")), true))
        storage.write(decisions)
        assertEquals(decisions, storage.read())
        saved = false
        assertThrows(IllegalStateException::class.java) { storage.write(emptyList()) }
        assertEquals(decisions, storage.read())
        json = "invalid"
        assertThrows(org.json.JSONException::class.java) { storage.read() }
    }
}
