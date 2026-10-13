/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.os.Bundle
import fr.sygix.tsuyu.persistence.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceControllerTest {
    private val direct = object : BoundedIo() { override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block() }
    private class Store : PersistenceStorage {
        var saved = StoredPersistence()
        override suspend fun read() = saved
        override suspend fun enable() { saved = saved.copy(enabled = true) }
        override suspend fun reserve(id: String, now: Long): RestartRecord? = null
        override suspend fun complete(record: RestartRecord, result: String) { saved = saved.copy(record = record.copy(outcome = result)) }
    }
    @Test fun viewingAndEnablementNeverMutateExpiryAndSystemChangeIsNeverReapplied() = runTest {
        val store = Store()
        store.saved = StoredPersistence(false, RestartRecord("boot:1", 1, 3, "EXHAUSTED"))
        var expiry = 604800000L
        var prepare = 0
        var expiryWrites = 0
        val server = object : TestServerStub() {
            override fun getUid() = 2000
            override fun isReady() = true
            override fun exit() = Unit
            override fun getPersistence() = Bundle().apply { putString("uid", "READY"); putString("package", "READY"); putBoolean("schedulable", true); putLong("expiry", expiry) }
            override fun preparePersistence(): Bundle { prepare++; return persistence }
            override fun disableKeyExpiry(): Bundle { expiryWrites++; expiry = 0; return persistence }
        }
        val coordinator = StartCoordinator(object : SystemSettings { override fun read() = true to Debugging.ENABLED }, object : ServerAccess { override suspend fun alive() = true }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) = Unit }, direct)
        val controller = PersistenceController(store, coordinator, { server }, direct)
        controller.refresh()
        assertEquals(0, prepare); assertEquals(0, expiryWrites); assertFalse(controller.state.value.enabled)
        controller.enable()
        assertTrue(controller.state.value.ready); assertTrue(store.saved.enabled)
        assertEquals(1, prepare); assertEquals(0, expiryWrites)
        assertEquals("RECOVERED", controller.state.value.outcome)
        controller.disableExpiry()
        assertEquals(0L, controller.state.value.expiry)
        controller.disableExpiry()
        assertEquals(1, expiryWrites)
        expiry = 123
        controller.refresh()
        assertEquals(123L, controller.state.value.expiry)
        assertEquals(1, expiryWrites)
    }
    @Test fun missingShellAndDisabledDebuggingDoNotPrepareOrEnable() = runTest {
        val store = Store()
        var launches = 0
        val coordinator = StartCoordinator(object : SystemSettings { override fun read() = true to Debugging.DISABLED }, object : ServerAccess { override suspend fun alive() = false }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) { launches++ } }, direct)
        val controller = PersistenceController(store, coordinator, { null }, direct)
        controller.enable()
        assertEquals(0, launches); assertFalse(store.saved.enabled)
        assertTrue(controller.state.value.setup)
        controller.disableExpiry()
        assertTrue(controller.state.value.error)
        assertNull(controller.state.value.expiry)
    }
    @Test fun failedFreshReadInvalidatesEarlierVerifiedModes() = runTest {
        val store = Store()
        var failed = false
        val server = object : TestServerStub() {
            override fun getUid() = 2000
            override fun isReady() = true
            override fun exit() = Unit
            override fun getPersistence(): Bundle {
                if (failed) throw IllegalStateException("Binder lookup failed")
                return Bundle().apply { putString("uid", "READY"); putString("package", "READY"); putBoolean("schedulable", true) }
            }
        }
        val settings = object : SystemSettings { override fun read() = true to Debugging.ENABLED }
        val coordinator = StartCoordinator(settings, object : ServerAccess { override suspend fun alive() = true }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) = Unit }, direct)
        val controller = PersistenceController(store, coordinator, { server }, direct)
        controller.refresh()
        assertTrue(controller.state.value.ready)
        failed = true
        controller.refresh()
        assertTrue(controller.state.value.error)
        assertFalse(controller.state.value.ready)
        assertNull(controller.state.value.expiry)
    }
}
