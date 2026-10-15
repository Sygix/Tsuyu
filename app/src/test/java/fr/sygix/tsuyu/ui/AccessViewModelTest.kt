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
            override fun getAccess() = android.os.Bundle().apply {
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
