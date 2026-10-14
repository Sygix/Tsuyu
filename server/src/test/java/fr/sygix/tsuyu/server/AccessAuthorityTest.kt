/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import org.junit.Assert.*
import org.junit.Test

class AccessAuthorityTest {
    private class Storage : AccessStorage {
        var values = emptyList<AccessDecision>()
        var fail = false
        override fun read() = values
        override fun write(decisions: List<AccessDecision>) { check(!fail); values = decisions }
    }
    @Test fun consentPersistsForNonPrimaryUserAndSharedUid() {
        val storage = Storage()
        val group = AccessGroup(110001, mapOf("first" to "signer", "second" to "signer"))
        val authority = AccessAuthority({ group }, storage)
        assertFalse(authority.allowed(group.uid))
        authority.decide(group.uid, true)
        assertTrue(authority.allowed(group.uid))
        assertTrue(AccessAuthority({ group }, storage).allowed(group.uid))
        authority.decide(group.uid, false)
        assertFalse(authority.allowed(group.uid))
        assertTrue(authority.rationale(group.uid))
    }
    @Test fun changedSignerMembershipAndUidReuseCannotInheritConsent() {
        val storage = Storage()
        var group = AccessGroup(10001, mapOf("first" to "signer"))
        val authority = AccessAuthority({ group }, storage)
        authority.decide(group.uid, true)
        group = group.copy(members = mapOf("first" to "new-signer"))
        assertFalse(authority.allowed(group.uid))
        authority.decide(group.uid, true)
        group = group.copy(members = group.members + ("second" to "new-signer"))
        assertFalse(authority.allowed(group.uid))
        group = group.copy(members = mapOf("replacement" to "new-signer"))
        assertFalse(authority.allowed(group.uid))
    }
    @Test fun failedCommitNeverPublishesGrant() {
        val storage = Storage()
        val group = AccessGroup(10001, mapOf("first" to "signer"))
        val authority = AccessAuthority({ group }, storage)
        storage.fail = true
        assertThrows(IllegalStateException::class.java) { authority.decide(group.uid, true) }
        assertFalse(authority.allowed(group.uid))
    }
    @Test fun restoringOldMembershipDoesNotRestoreInvalidatedConsent() {
        val storage = Storage()
        val original = AccessGroup(10001, mapOf("first" to "signer"))
        var group = original
        val authority = AccessAuthority({ group }, storage)
        authority.decide(group.uid, true)
        group = original.copy(members = original.members + ("second" to "signer"))
        assertFalse(authority.allowed(group.uid))
        group = original
        assertFalse(authority.allowed(group.uid))
    }
    @Test fun installedSystemClientsStillRequireExplicitMatchingConsent() {
        val storage = Storage()
        val group = AccessGroup(1000, mapOf("platform-client" to "platform-signer"))
        val authority = AccessAuthority({ group }, storage)
        assertFalse(authority.allowed(group.uid))
        authority.decide(group.uid, true)
        assertTrue(authority.allowed(group.uid))
        authority.decide(group.uid, false)
        assertFalse(authority.allowed(group.uid))
        assertThrows(IllegalArgumentException::class.java) { AccessGroup(-1, group.members) }
    }
    @Test fun aPendingDecisionCannotGrantTheIdentityThatReplacedItsDisplayedGroup() {
        val storage = Storage()
        val expected = AccessGroup(10001, mapOf("first" to "signer"))
        val changed = expected.copy(members = expected.members + ("second" to "signer"))
        val authority = AccessAuthority({ changed }, storage)
        assertThrows(IllegalStateException::class.java) { authority.decide(expected, true) }
        assertFalse(authority.allowed(expected.uid))
        assertTrue(storage.values.isEmpty())
    }
}
