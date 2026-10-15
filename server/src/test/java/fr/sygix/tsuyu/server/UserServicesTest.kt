/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.content.ComponentName
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import moe.shizuku.server.IShizukuServiceConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.ShizukuApiConstants.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserServicesTest {
    private val group = AccessGroup(10001, mapOf("client" to "signer"))
    private class Child : Process() {
        var running = true
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor() = 0
        override fun exitValue(): Int { check(!running); return 0 }
        override fun destroy() { running = false }
        override fun isAlive() = running
    }
    private class Connection : IShizukuServiceConnection.Stub() {
        var death: IBinder.DeathRecipient? = null
        var unlinked = false
        val attached = CountDownLatch(1)
        override fun connected(service: IBinder) { attached.countDown() }
        override fun died() = Unit
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { death = recipient }
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean { unlinked = true; return true }
    }
    private fun options(version: Int = 1, daemon: Boolean = true, peek: Boolean = false, remove: Boolean = true) = Bundle().apply {
        putParcelable(USER_SERVICE_ARG_COMPONENT, ComponentName("client", "client.Service"))
        putInt(USER_SERVICE_ARG_VERSION_CODE, version)
        putBoolean(USER_SERVICE_ARG_DAEMON, daemon)
        putBoolean(USER_SERVICE_ARG_NO_CREATE, peek)
        putBoolean(USER_SERVICE_ARG_REMOVE, remove)
    }
    @Test fun versionsPeekDaemonAndExplicitRemovalFollowTheUpstreamContract() {
        val children = mutableListOf<Child>()
        val commands = mutableListOf<List<String>>()
        val services = UserServices("apk", 0, { true }, { command -> commands += command; Child().also { children += it } }, { it.destroy() }, { 123 })
        try {
            val connection = Connection()
            assertEquals(-1, services.add(group.uid, group, 13, connection, options(peek = true)))
            services.add(group.uid, group, 13, connection, options(version = 7))
            services.attach(Binder(), commands.last().last(), 123)
            assertTrue(connection.attached.await(3, TimeUnit.SECONDS))
            assertEquals(7, services.add(group.uid, group, 13, connection, options(peek = true)))
            services.unbind(group.uid, group, connection, options(remove = false))
            assertTrue(connection.unlinked)
            assertTrue(children.single().running)
            services.add(group.uid, group, 13, Connection(), options(version = 8))
            assertFalse(children.first().running)
            assertEquals(2, children.size)
            services.revoke(group.uid)
            assertTrue(children.none { it.running })
        } finally { services.close() }
    }
    @Test fun attachmentTimeoutAndNonDaemonConnectionDeathPermitAFreshLaunch() {
        val children = mutableListOf<Child>()
        val expirations = mutableListOf<() -> Unit>()
        val stopped = CountDownLatch(1)
        val services = UserServices("apk", 0, { true }, { Child().also { children += it } }, { it.destroy(); stopped.countDown() }, { 123 }, { expirations += it })
        try {
            services.add(group.uid, group, 13, Connection(), options())
            expirations.single()()
            assertTrue(stopped.await(3, TimeUnit.SECONDS))
            assertEquals(-1, services.add(group.uid, group, 13, Connection(), options(peek = true)))
            val connection = Connection()
            services.add(group.uid, group, 13, connection, options(daemon = false))
            requireNotNull(connection.death).binderDied()
            services.revoke(group.uid)
            assertTrue(children.none { it.running })
        } finally { services.close() }
    }
    @Test fun cleanupWaitDoesNotHoldTheGlobalRegistryLock() {
        val stopping = CountDownLatch(1)
        val release = CountDownLatch(1)
        val services = UserServices("apk", 0, { true }, { Child() }, { stopping.countDown(); check(release.await(3, TimeUnit.SECONDS)); it.destroy() })
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            services.add(group.uid, group, 13, Connection(), options())
            val revoked = executor.submit { services.revoke(group.uid) }
            assertTrue(stopping.await(3, TimeUnit.SECONDS))
            assertEquals(-1, services.add(group.uid, group, 13, Connection(), options(peek = true)))
            release.countDown()
            revoked.get(3, TimeUnit.SECONDS)
        } finally { release.countDown(); executor.shutdownNow(); services.close() }
    }
}
