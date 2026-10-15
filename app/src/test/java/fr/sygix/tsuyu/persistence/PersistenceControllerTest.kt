/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import android.os.Bundle
import fr.sygix.tsuyu.start.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import fr.sygix.tsuyu.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceControllerTest {
    private val direct = object : BoundedIo() { override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block() }
    private class Store : FakePersistenceStorage() {
        var saved: StoredPersistence
            get() = value
            set(value) { this.value = value }
        var failRead = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun read(): StoredPersistence { gate?.await(); check(!failRead); return saved }
    }
    @Test fun routingWaitsForConfigurationAndReadFailureIsVisible() = runTest {
        val store = Store()
        store.saved = StoredPersistence(enabled = true)
        val gate = CompletableDeferred<Unit>()
        store.gate = gate
        val coordinator = StartCoordinator(object : SystemSettings { override fun read() = true to Debugging.ENABLED }, object : ServerAccess { override suspend fun alive() = false }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) = Unit }, direct)
        val controller = PersistenceController(store, coordinator, { null }, direct)
        val route = async { controller.startRoute() }
        runCurrent()
        assertFalse(route.isCompleted)
        assertFalse(controller.state.value.enabledKnown)
        gate.complete(Unit)
        assertEquals(StartRoute.START, route.await())
        assertTrue(controller.state.value.enabledKnown)
        store.failRead = true
        assertEquals(StartRoute.ERROR, controller.startRoute())
        assertTrue(controller.state.value.error)
        assertFalse(controller.state.value.enabledKnown)
    }
    @Test fun deniedFailedAndTimedOutPreparationExposeRepairInsteadOfDisclosure() = runTest {
        for (mode in listOf("DENIED", "FAILED", "TIMEOUT")) {
            val store = Store()
            val server = object : TestServerStub() {
                override fun getUid() = 2000
                override fun isReady() = true
                override fun exit() = Unit
                override fun preparePersistence() = Bundle().apply { putString("uid", mode); putString("package", mode); putBoolean("schedulable", false) }
            }
            val coordinator = StartCoordinator(object : SystemSettings { override fun read() = true to Debugging.ENABLED }, object : ServerAccess { override suspend fun alive() = true }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) = Unit }, direct)
            var preparationBudget = 0L
            val measured = object : BoundedIo() {
                override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T {
                    preparationBudget = maxOf(preparationBudget, timeout)
                    return block()
                }
            }
            val controller = PersistenceController(store, coordinator, { server }, measured)
            controller.enable()
            assertFalse(store.saved.enabled)
            assertTrue(controller.state.value.error)
            val ui = PersistenceViewModel.present(controller.state.value, false, "persistence-enable")
            assertEquals(R.string.persistence_error, ui.body)
            assertEquals(R.string.persistence_repair, ui.controls.first().label)
            assertTrue(preparationBudget > 7 * 5000)
            controller.settingsFailed()
            assertEquals(R.string.error_settings, PersistenceViewModel.present(controller.state.value, false, "persistence-enable").body)
        }
    }
    @Test fun viewingAndEnablementNeverMutateExpiryAndSystemChangeIsNeverReapplied() = runTest {
        val store = Store()
        store.saved = StoredPersistence(false, RestartRecord("boot:1", 1, 3, RestartOutcome.EXHAUSTED))
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
        assertEquals(RestartOutcome.RECOVERED, controller.state.value.outcome)
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
