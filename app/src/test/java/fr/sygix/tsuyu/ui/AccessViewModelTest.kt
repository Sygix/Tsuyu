/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import fr.sygix.tsuyu.access.AccessViewModel
import fr.sygix.tsuyu.start.BoundedIo
import fr.sygix.tsuyu.start.TestServerStub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessViewModelTest {
    @Test fun refusedRowsFollowAuthorizedOnesAndAFailedResetKeepsTheRowFocused() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var resets = 0
        var unchanged = false
        val server = object : TestServerStub() {
            override fun getUid() = 2000
            override fun isReady() = true
            override fun exit() = Unit
            var reloadFails = false
            override fun getAccess() = if (reloadFails) error("Service stopped") else android.os.Bundle().apply {
                putIntArray("uids", intArrayOf(10002, 10001))
                putIntArray("refused", intArrayOf(10002))
                putStringArrayList("10001", arrayListOf("zz.authorized"))
                putStringArrayList("10002", arrayListOf("aa.refused"))
            }
            override fun resetRefusal(uid: Int): Boolean {
                assertEquals(10002, uid)
                resets++
                check(resets > 1) { "Storage unavailable" }
                return !unchanged
            }
        }
        val direct = object : BoundedIo() {
            override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
        }
        try {
            val model = AccessViewModel(context, { server }, null, direct)
            store.put("access", model)
            advanceUntilIdle()
            assertEquals(listOf(10001, 10002), model.ui.value.rows.map { it.uid })
            assertEquals(listOf("revoke-10001", "reset-10002", "access-back"), model.ui.value.presentation.controls.map { it.id })
            model.reset(10002)
            advanceUntilIdle()
            assertEquals(10002, model.ui.value.resetFailed)
            assertEquals("reset-10002", model.ui.value.presentation.focus)
            assertEquals(fr.sygix.tsuyu.R.string.access_reset_error, model.ui.value.presentation.body)
            assertTrue(model.ui.value.rows.single { it.uid == 10002 }.refused)
            model.reset(10002)
            advanceUntilIdle()
            assertEquals(listOf(10001), model.ui.value.rows.map { it.uid })
            assertNull(model.ui.value.resetFailed)
            assertEquals("revoke-10001", model.ui.value.presentation.focus)
            unchanged = true
            model.refresh()
            advanceUntilIdle()
            model.reset(10002)
            advanceUntilIdle()
            assertEquals(listOf(10001, 10002), model.ui.value.rows.map { it.uid })
            assertNull(model.ui.value.resetFailed)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun returningToTheAccessListReloadsItButARequestIsNotReloaded() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var loads = 0
        var requests = 0
        val server = object : TestServerStub() {
            override fun getUid() = 2000
            override fun isReady() = true
            override fun exit() = Unit
            var revokeFails = false
            var revokes = 0
            var reloadFails = false
            override fun getAccess() = if (reloadFails) error("Service stopped") else android.os.Bundle().apply {
                putIntArray("uids", intArrayOf(10001, 10002))
                putStringArrayList("10001", arrayListOf("first"))
                putStringArrayList("10002", arrayListOf("second"))
            }.also { loads++ }
            override fun getRequest(token: String) = android.os.Bundle().apply { putInt("uid", 10001); putStringArrayList("packages", arrayListOf("client")) }.also { requests++ }
            override fun revoke(uid: Int) { revokes++; check(!revokeFails) { "Revocation incomplete" } }
        }
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        val direct = object : BoundedIo() {
            override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T { gate?.await(); return block() }
        }
        try {
            val list = AccessViewModel(context, { server }, null, direct)
            store.put("list", list)
            advanceUntilIdle()
            list.resumed(); advanceUntilIdle()
            assertEquals(1, loads)
            list.focused(10002)
            val shown = list.ui.value.presentation.controls.map { it.id }
            gate = kotlinx.coroutines.CompletableDeferred()
            list.resumed(); runCurrent()
            assertFalse(list.ui.value.loading)
            assertEquals(shown, list.ui.value.presentation.controls.map { it.id })
            gate!!.complete(Unit); gate = null
            advanceUntilIdle()
            assertEquals(2, loads)
            assertEquals(10002, list.ui.value.focus)
            assertEquals("revoke-10002", list.ui.value.presentation.focus)
            gate = kotlinx.coroutines.CompletableDeferred()
            val stale = gate
            list.resumed(); runCurrent()
            gate = null
            list.revoke(10001); advanceUntilIdle()
            stale!!.complete(Unit); advanceUntilIdle()
            assertEquals(listOf(10002), list.ui.value.rows.map { it.uid })
            server.reloadFails = true
            list.resumed(); advanceUntilIdle()
            assertTrue(list.ui.value.error)
            server.reloadFails = false
            list.refresh(); advanceUntilIdle()
            server.revokeFails = true
            list.revoke(10002); advanceUntilIdle()
            assertEquals(10002, list.ui.value.retryRevoke)
            val loadsBefore = loads
            list.resumed(); advanceUntilIdle()
            assertEquals(2, server.revokes)
            assertEquals(loadsBefore, loads)
            val request = AccessViewModel(context, { server }, "token", direct)
            store.put("request", request)
            advanceUntilIdle()
            request.resumed(); request.resumed(); advanceUntilIdle()
            assertEquals(1, requests)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun expiredDeadlinesEndLoadingWithAnActionableErrorOnEveryCall() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val server = object : TestServerStub() {
            override fun getUid() = 2000
            override fun isReady() = true
            override fun exit() = Unit
        }
        val expiring = object : BoundedIo() {
            override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T =
                kotlinx.coroutines.withTimeout(timeout) { kotlinx.coroutines.awaitCancellation() }
        }
        try {
            val request = AccessViewModel(context, { server }, "token", expiring)
            store.put("request", request)
            advanceUntilIdle()
            assertFalse(request.ui.value.loading)
            assertTrue(request.ui.value.error)
            request.confirm(true)
            advanceUntilIdle()
            assertTrue(request.ui.value.error)
            assertFalse(request.ui.value.finished)
            val list = AccessViewModel(context, { server }, null, expiring)
            store.put("list", list)
            advanceUntilIdle()
            list.revoke(10001)
            advanceUntilIdle()
            assertFalse(list.ui.value.loading)
            assertTrue(list.ui.value.error)
            assertEquals(10001, list.ui.value.retryRevoke)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun retryFinishesRevocationInsteadOfReloadingAwayTheFailedRow() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var reads = 0
        var revokes = 0
        val server = object : TestServerStub() {
            override fun getUid() = 2000
            override fun isReady() = true
            override fun exit() = Unit
            var reloadFails = false
            override fun getAccess() = if (reloadFails) error("Service stopped") else android.os.Bundle().apply {
                reads++
                putIntArray("uids", intArrayOf(10001))
                putStringArrayList("10001", arrayListOf(context.packageName))
            }
            override fun revoke(uid: Int) {
                assertEquals(10001, uid)
                revokes++
                check(revokes > 1) { "Cleanup still pending" }
            }
        }
        val direct = object : BoundedIo() {
            override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
        }
        try {
            val model = AccessViewModel(context, { server }, null, direct)
            store.put("access", model)
            advanceUntilIdle()
            model.revoke(10001)
            advanceUntilIdle()
            assertTrue(model.ui.value.error)
            assertEquals(10001, model.ui.value.retryRevoke)
            model.refresh()
            advanceUntilIdle()
            assertEquals(2, revokes)
            assertEquals(1, reads)
            assertTrue(model.ui.value.rows.isEmpty())
            assertFalse(model.ui.value.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun backCanLeaveAnUnavailablePermissionRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val direct = object : BoundedIo() {
            override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
        }
        try {
            val model = AccessViewModel(ApplicationProvider.getApplicationContext(), { null }, "pending", direct)
            store.put("access", model)
            advanceUntilIdle()
            assertTrue(model.ui.value.error)
            model.back()
            advanceUntilIdle()
            assertTrue(model.ui.value.finished)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
