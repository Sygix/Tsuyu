/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.lifecycle.ViewModelStore
import fr.sygix.tsuyu.start.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val direct = object : BoundedIo() {
        override suspend fun <T> call(timeout: Long, cancel: () -> Unit, block: () -> T): T = block()
    }
    @Test fun returningFromSettingsStartsAndBackRestoresHomeFocus() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var debugging = Debugging.DISABLED
            var running = false
            var launches = 0
            val coordinator = StartCoordinator(object : SystemSettings {
                override fun read() = true to debugging
            }, object : ServerAccess {
                override suspend fun alive() = running
            }, object : ServerLauncher {
                override suspend fun launch(progress: (Phase) -> Unit) { launches++; running = true }
            }, direct)
            val model = HomeViewModel(coordinator)
            store.put("home", model)
            model.resume(); runCurrent()
            model.act(Action.ENABLE); runCurrent()
            assertEquals(Screen.SETUP, model.ui.value.screen)
            assertEquals("action-settings", model.ui.value.focus)
            debugging = Debugging.ENABLED
            model.resume(); advanceUntilIdle()
            assertEquals(1, launches)
            assertEquals(Screen.HOME, model.ui.value.screen)
            assertEquals(Action.DETAILS, model.ui.value.controls.first().action)
            model.act(Action.HELP); runCurrent()
            model.back(); advanceUntilIdle()
            assertEquals(Screen.HOME, model.ui.value.screen)
            assertEquals("action-help", model.ui.value.focus)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun cancelAutomaticallyReconcilesLateLiveServerWithoutAnotherResume() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var running = false
            val cleanup = CompletableDeferred<Unit>()
            val coordinator = StartCoordinator(object : SystemSettings {
                override fun read() = true to Debugging.ENABLED
            }, object : ServerAccess {
                override suspend fun alive() = running
            }, object : ServerLauncher {
                override suspend fun launch(progress: (Phase) -> Unit) {
                    try { awaitCancellation() }
                    finally { withContext(NonCancellable) { cleanup.await(); running = true } }
                }
            }, direct)
            val model = HomeViewModel(coordinator)
            store.put("home", model)
            model.act(Action.START); runCurrent()
            model.act(Action.CANCEL); runCurrent()
            cleanup.complete(Unit); advanceUntilIdle()
            assertEquals(Screen.HOME, model.ui.value.screen)
            assertEquals(Action.DETAILS, model.ui.value.controls.first().action)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
