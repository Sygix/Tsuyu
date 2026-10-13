/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import fr.sygix.tsuyu.adb.*
import fr.sygix.tsuyu.ipc.ITsuyuServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAdbLauncherTest {
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }
    private fun context(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        info.sourceDir = "/data/app/fixture/base.apk"
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = context.packageName; applicationInfo = info })
        return context
    }
    private fun publish(context: Context, broker: ServerBroker) {
        val preferences = context.getSharedPreferences("launch", Context.MODE_PRIVATE)
        broker.receive(2000, Bundle().apply {
            putString("token", preferences.getString("token", null))
            putString("generation", preferences.getString("generation", null))
            putBinder("server", object : TestServerStub() {
                override fun getUid() = 2000
                override fun isReady() = true
                override fun exit() = Unit
            })
        })
    }
    @Test fun fallbackOnlyUsesValidCandidatesAndClosesLaunchConnection() = runTest {
        val context = context()
        val broker = ServerBroker(context)
        val attempted = mutableListOf<Int>()
        var closes = 0
        val phases = mutableListOf<Phase>()
        val factory = AdbConnectionFactory { object : AdbConnection {
            override fun connect(port: Int) { attempted += port; if (port != 5555) throw IOException("No listener") }
            override fun authenticate(key: AdbKey, awaiting: () -> Unit) = awaiting()
            override fun shell(command: String): String { publish(context, broker); return "TSUYU_LAUNCHED" }
            override fun close() { closes++ }
        } }
        LocalAdbLauncher(context, broker, direct, factory) { listOf(-1, 70000, 5557, 5555, 5555) }.launch(phases::add)
        assertEquals(listOf(5557, 5555), attempted)
        assertEquals(2, closes)
        assertEquals(listOf(Phase.AUTHORIZING, Phase.LAUNCHING), phases)
        assertEquals(2000, broker.server.value?.uid)
    }
    @Test fun refusalTlsAndTimeoutAreDistinctAndReleaseConnections() = runTest {
        for ((cause, expected) in listOf(EOFException() to Failure.AUTHENTICATION, UnsupportedAdb() to Failure.UNSUPPORTED, SocketTimeoutException() to Failure.TIMEOUT)) {
            val context = context()
            val broker = ServerBroker(context)
            var closed = false
            val factory = AdbConnectionFactory { object : AdbConnection {
                override fun connect(port: Int) = Unit
                override fun authenticate(key: AdbKey, awaiting: () -> Unit) { throw cause }
                override fun shell(command: String): String = error("Must not launch")
                override fun close() { closed = true }
            } }
            val result = runCatching { LocalAdbLauncher(context, broker, direct, factory) { listOf(5555) }.launch {} }
            assertEquals(expected, (result.exceptionOrNull() as StartException).failure)
            assertTrue(closed)
            assertNull(broker.server.value)
        }
    }
    @Test fun noListenerReportsEndpointWithoutLaunching() = runTest {
        val context = context()
        var closed = false
        val factory = AdbConnectionFactory { object : AdbConnection {
            override fun connect(port: Int) { throw IOException("No listener") }
            override fun authenticate(key: AdbKey, awaiting: () -> Unit) = error("Must not authenticate")
            override fun shell(command: String): String = error("Must not launch")
            override fun close() { closed = true }
        } }
        val failure = runCatching { LocalAdbLauncher(context, ServerBroker(context), direct, factory) { listOf(5555) }.launch {} }.exceptionOrNull() as StartException
        assertEquals(Failure.ENDPOINT, failure.failure)
        assertTrue(closed)
    }
    @Test fun authenticatedLaunchDeadlineReportsLaunchFailureAndKeepsTheKeyForRetry() = runTest {
        val context = context()
        val broker = ServerBroker(context)
        val keys = mutableListOf<ByteArray>()
        var attempts = 0
        var closes = 0
        val factory = AdbConnectionFactory { object : AdbConnection {
            override fun connect(port: Int) = Unit
            override fun authenticate(key: AdbKey, awaiting: () -> Unit) { keys += key.publicKey() }
            override fun shell(command: String): String {
                attempts++
                if (attempts == 1) return "TSUYU_LAUNCHED"
                publish(context, broker)
                return "TSUYU_LAUNCHED"
            }
            override fun close() { closes++ }
        } }
        val launcher = LocalAdbLauncher(context, broker, direct, factory) { listOf(5555) }
        val coordinator = StartCoordinator(object : SystemSettings {
            override fun read() = true to Debugging.ENABLED
        }, object : ServerAccess {
            override suspend fun alive() = broker.server.value != null
        }, launcher, direct)
        coordinator.start()
        assertEquals(Phase.ERROR, coordinator.state.value.phase)
        assertEquals(Failure.LAUNCH, coordinator.state.value.failure)
        coordinator.start()
        assertEquals(Phase.RUNNING, coordinator.state.value.phase)
        assertArrayEquals(keys[0], keys[1])
        assertEquals(2, closes)
    }
    @Test fun shellFailureAndSocketDeadlineAfterAuthenticationAreLaunchErrors() = runTest {
        for (output in listOf("TSUYU_FAILED", "TSUYU_TIMEOUT", "prefixTSUYU_LAUNCHED", null)) {
            val context = context()
            var closed = false
            val factory = AdbConnectionFactory { object : AdbConnection {
                override fun connect(port: Int) = Unit
                override fun authenticate(key: AdbKey, awaiting: () -> Unit) = Unit
                override fun shell(command: String): String = output ?: throw SocketTimeoutException()
                override fun close() { closed = true }
            } }
            val failure = runCatching {
                LocalAdbLauncher(context, ServerBroker(context), direct, factory) { listOf(5555) }.launch {}
            }.exceptionOrNull() as StartException
            assertEquals(Failure.LAUNCH, failure.failure)
            assertTrue(closed)
        }
    }
}
