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
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.ShizukuApiConstants.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClientServiceTest {
    private var uid = 110001
    private var pid = 123
    private var group = AccessGroup(uid, mapOf("client" to "signer"))
    private val storage = object : AccessStorage {
        var decisions = emptyList<AccessDecision>()
        override fun read() = decisions
        override fun write(decisions: List<AccessDecision>) { this.decisions = decisions }
    }
    private val deadline = Deadline()
    private val identities = ClientIdentity(deadline) { group }
    private val access = AccessAuthority(identities::resolve, storage)
    private var time = 0L
    private val opened = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, Int>>()
    private val results = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, Boolean>>()
    private var death: android.os.IBinder.DeathRecipient? = null
    private val service = ClientService(10002, access, identities, storage, deadline, {}, UserServices("unused", 1), { uid }, { pid }, { "shell-context" }, elapsed = { time }, showRequest = { token, user -> opened.add(token to user) }, stopClient = { _, _ -> })
    @After fun cleanup() { service.shutdown(); deadline.close() }
    private var metadata: Bundle? = null
    private val callback = object : IShizukuApplication.Stub() {
        override fun bindApplication(data: Bundle) { metadata = data }
        override fun dispatchRequestPermissionResult(code: Int, data: Bundle) { results.add(code to data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED)) }
        override fun linkToDeath(recipient: android.os.IBinder.DeathRecipient, flags: Int) { death = recipient }
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
        val api: IShizukuService = service
        api.dispatchPackageChanged(null)
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
    @Test fun zeroMaskAndClearingDenialDoNotRevokeAnExistingGrant() {
        attach(); access.decide(uid, true)
        val clientUid = uid
        uid = 10002
        service.updateFlagsForUid(clientUid, 0, 0)
        assertTrue(access.allowed(clientUid))
        service.updateFlagsForUid(clientUid, 4, 0)
        assertTrue(access.allowed(clientUid))
        deadline.close()
    }
    @Test fun listingChangedGroupPermanentlyInvalidatesItsConsent() {
        val original = group
        access.decide(uid, true)
        val clientUid = uid
        group = original.copy(members = original.members + ("second" to "signer"))
        uid = 10002
        assertEquals(0, service.accessList().getIntArray("uids")?.size)
        group = original
        assertFalse(access.allowed(clientUid))
        deadline.close()
    }
    @Test fun upstreamRemovalAcceptsItsUnusedNullConnection() {
        attach(); access.decide(uid, true)
        val api: IShizukuService = service
        val options = Bundle().apply {
            putParcelable(USER_SERVICE_ARG_COMPONENT, android.content.ComponentName("client", "client.Service"))
            putBoolean(USER_SERVICE_ARG_REMOVE, true)
        }
        assertEquals(1, api.removeUserService(null, options))
        deadline.close()
    }
    @Test fun upstreamPropertyGetterAcceptsItsNullDefault() {
        attach(); access.decide(uid, true)
        val api: IShizukuService = service
        assertEquals("", api.getSystemProperty("tsuyu.test.missing", null))
        deadline.close()
    }
    @Test fun explicitConsentReturnsMatchingCodeAndReplayCannotChangeIt() {
        attach(); service.requestPermission(73)
        val token = opened.single().first
        assertEquals(1, opened.single().second)
        uid = 10002
        assertEquals(110001, service.request(token).getInt("uid"))
        service.confirm(token, true)
        service.confirm(token, false)
        assertEquals(listOf(73 to true), results.toList())
        uid = 110001
        assertTrue(service.checkSelfPermission())
    }
    @Test fun refusalAndLateConfirmationReturnDenialWithTheOriginalCode() {
        attach(); service.requestPermission(17)
        val token = opened.single().first
        time = 120001
        uid = 10002
        service.confirm(token, true)
        assertEquals(listOf(17 to false), results.toList())
        uid = 110001
        assertFalse(service.checkSelfPermission())
        assertTrue(service.shouldShowRequestPermissionRationale())
    }
    @Test fun clientDeathCannotAuthorizePendingConsent() {
        attach(); service.requestPermission(19)
        val token = opened.single().first
        requireNotNull(death).binderDied()
        uid = 10002
        service.confirm(token, true)
        assertTrue(storage.read().isEmpty())
        assertTrue(results.isEmpty())
    }
    @Test fun pendingRequestsAreFiniteAndDuplicateCodesCannotCreateAnotherPrompt() {
        attach(); service.requestPermission(1)
        assertThrows(IllegalStateException::class.java) { service.requestPermission(1) }
        service.requestPermission(2)
        assertThrows(IllegalStateException::class.java) { service.requestPermission(3) }
        assertEquals(2, opened.size)
        assertFalse(service.checkSelfPermission())
    }
    @Test fun signerChangeBetweenRequestAndConfirmationNeverPublishesSuccess() {
        attach(); service.requestPermission(23)
        val token = opened.single().first
        group = group.copy(members = mapOf("client" to "changed-signer"))
        uid = 10002
        service.confirm(token, true)
        assertEquals(listOf(23 to false), results.toList())
        assertTrue(storage.read().isEmpty())
    }
    @Test fun groupConsentCompletesEveryMatchingPendingCodeAndAlreadyGrantedRequests() {
        group = group.copy(members = mapOf("client" to "signer", "second" to "signer"))
        attach(); service.requestPermission(47)
        val secondResults = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, Boolean>>()
        val second = object : IShizukuApplication.Stub() {
            override fun bindApplication(data: Bundle) = Unit
            override fun dispatchRequestPermissionResult(code: Int, data: Bundle) { secondResults.add(code to data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED)) }
            override fun showPermissionConfirmation(uid: Int, pid: Int, name: String, code: Int) = Unit
        }
        pid = 124
        service.attachApplication(second, Bundle().apply { putString(ATTACH_APPLICATION_PACKAGE_NAME, "second"); putInt(ATTACH_APPLICATION_API_VERSION, 13) })
        service.requestPermission(48)
        uid = 10002
        service.confirm(opened.first().first, true)
        assertEquals(listOf(47 to true), results.toList())
        assertEquals(listOf(48 to true), secondResults.toList())
        uid = 110001
        assertEquals(13, service.getVersion())
        service.requestPermission(49)
        assertEquals(listOf(48 to true, 49 to true), secondResults.toList())
        assertEquals(2, opened.size)
        pid = 123
        assertEquals(13, service.getVersion())
    }
    @Test fun onewayWithUnavailablePidStillRequiresAnAttachedConsentingUid() {
        attach(); access.decide(uid, true); pid = 0
        var calls = 0
        val target = object : android.os.Binder() {
            override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
                assertEquals(27, code)
                assertEquals(android.os.IBinder.FLAG_ONEWAY, flags)
                calls++
                return true
            }
        }
        fun forward() {
            val data = android.os.Parcel.obtain()
            try {
                data.writeInterfaceToken(BINDER_DESCRIPTOR)
                data.writeStrongBinder(target); data.writeInt(27); data.writeInt(android.os.IBinder.FLAG_ONEWAY)
                assertTrue(service.transact(1, data, null, android.os.IBinder.FLAG_ONEWAY))
            } finally { data.recycle() }
        }
        forward()
        assertEquals(1, calls)
        access.decide(uid, false)
        forward()
        assertEquals(1, calls)
        uid = 110002
        forward()
        assertEquals(1, calls)
    }
    @Test fun revocationClearsTheDecisionSoAnUnchangedClientCanRequestAgain() {
        attach(); access.decide(uid, true)
        val clientUid = uid
        uid = 10002
        service.revoke(clientUid)
        assertTrue(storage.read().isEmpty())
        uid = clientUid
        assertFalse(service.checkSelfPermission())
        assertFalse(service.shouldShowRequestPermissionRationale())
        service.requestPermission(91)
        assertEquals(1, opened.size)
        uid = 10002
        service.confirm(opened.single().first, true)
        uid = clientUid
        assertTrue(service.checkSelfPermission())
        assertEquals(listOf(91 to true), results.toList())
    }
}
