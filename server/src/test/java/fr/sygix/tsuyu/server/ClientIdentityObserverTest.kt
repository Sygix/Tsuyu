/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.content.BroadcastReceiver
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class ClientIdentityObserverTest {
    @Test fun packageChangesInvalidateCachedSignatureIdentityWithoutAnApplicationContext() {
        val deadline = Deadline()
        try {
            var group = AccessGroup(10001, mapOf("client" to "first"))
            val identity = ClientIdentity(deadline) { group }
            var receiver: BroadcastReceiver? = null
            var invalidations = 0
            identity.observe(7, { invalidations++ }) { actual, filter, user ->
                assertEquals(7, user)
                for (action in listOf(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_REPLACED)) assertTrue(filter.hasAction(action))
                assertTrue(filter.hasDataScheme("package"))
                receiver = actual
            }
            assertEquals(group, identity.resolve(group.uid))
            group = group.copy(members = mapOf("client" to "replacement"))
            assertNotEquals(group, identity.resolve(group.uid))
            requireNotNull(receiver).onReceive(null, Intent(Intent.ACTION_PACKAGE_REPLACED))
            assertEquals(1, invalidations)
            assertEquals(group, identity.resolve(group.uid))
        } finally { deadline.close() }
    }

    @Test fun failedRegistrationDoesNotEnableStaleIdentityCaching() {
        val deadline = Deadline()
        try {
            var group = AccessGroup(10001, mapOf("client" to "first"))
            val identity = ClientIdentity(deadline) { group }
            assertThrows(Exception::class.java) {
                identity.observe(0, {}) { _, _, _ -> throw SecurityException("Denied") }
            }
            assertEquals(group, identity.resolve(group.uid))
            group = group.copy(members = mapOf("client" to "replacement"))
            assertEquals(group, identity.resolve(group.uid))
        } finally { deadline.close() }
    }
}
