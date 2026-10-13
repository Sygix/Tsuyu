/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.content.Intent
import android.os.Bundle
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.ShizukuApiConstants.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClientServiceTest {
    private var uid = 110001
    private var group = AccessGroup(uid, mapOf("client" to "signer"))
    private val storage = object : AccessStorage {
        var decisions = emptyList<AccessDecision>()
        override fun read() = decisions
        override fun write(decisions: List<AccessDecision>) { this.decisions = decisions }
    }
    private val deadline = Deadline()
    private val identities = ClientIdentity(deadline) { group }
    private val access = AccessAuthority(identities::resolve, storage)
    private val service = ClientService(10002, access, identities, storage, deadline, {}, UserServices("unused", 1), { uid }, { 123 }, { "shell-context" })
    private var metadata: Bundle? = null
    private val callback = object : IShizukuApplication.Stub() {
        override fun bindApplication(data: Bundle) { metadata = data }
        override fun dispatchRequestPermissionResult(code: Int, data: Bundle) = Unit
        override fun showPermissionConfirmation(uid: Int, pid: Int, name: String, code: Int) = Unit
    }
    private fun attach(name: String = "client") = service.attachApplication(callback, Bundle().apply { putString(ATTACH_APPLICATION_PACKAGE_NAME, name); putInt(ATTACH_APPLICATION_API_VERSION, 13) })
    @Test fun attachmentRejectsSpoofAndPrivilegesRequireConsent() {
        assertThrows(SecurityException::class.java) { service.getVersion() }
        assertThrows(IllegalArgumentException::class.java) { attach("spoof") }
        attach()
        assertEquals(13, metadata?.getInt(BIND_APPLICATION_SERVER_VERSION))
        assertEquals(6, metadata?.getInt(BIND_APPLICATION_SERVER_PATCH_VERSION))
        assertFalse(service.checkSelfPermission())
        assertThrows(SecurityException::class.java) { service.getVersion() }
        access.decide(uid, true)
        assertEquals(13, service.getVersion())
        assertEquals("shell-context", service.getSELinuxContext())
        group = group.copy(members = mapOf("replacement" to "other-signer"))
        assertFalse(service.checkSelfPermission())
        assertThrows(SecurityException::class.java) { service.getVersion() }
        deadline.close()
    }
    @Test fun clientsCannotAdministerEvenWithGrantedBinder() {
        attach(); access.decide(uid, true)
        assertFalse(service.isHidden(uid))
        assertThrows(SecurityException::class.java) { service.exit() }
        assertThrows(SecurityException::class.java) { service.updateFlagsForUid(uid, 6, 2) }
        assertThrows(SecurityException::class.java) { service.dispatchPackageChanged(Intent()) }
        uid = 10002
        service.dispatchPackageChanged(Intent())
        assertEquals(1, storage.read().size)
        assertTrue(storage.read().single().allowed)
        deadline.close()
    }
    @Test fun wireIdsRemainSparseAndDoNotCollideWithForwarding() {
        val expected = mapOf("getVersion" to 3, "getUid" to 4, "newProcess" to 8, "attachApplication" to 18, "exit" to 101, "updateFlagsForUid" to 107)
        expected.forEach { (method, code) ->
            val field = IShizukuService.Stub::class.java.getDeclaredField("TRANSACTION_$method").apply { isAccessible = true }
            assertEquals(code, field.getInt(null))
        }
        assertEquals(1, BINDER_TRANSACTION_transact)
        deadline.close()
    }
}
