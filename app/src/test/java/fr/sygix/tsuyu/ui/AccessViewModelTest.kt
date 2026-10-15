/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import fr.sygix.tsuyu.access.AccessViewModel
import fr.sygix.tsuyu.start.BoundedIo
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
