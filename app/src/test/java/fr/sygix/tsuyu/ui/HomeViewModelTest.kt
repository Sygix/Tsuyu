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
    @Test fun languageReturnKeepsItsFocusThroughServiceDeathAndPreparationFailureWithoutStartingAnything() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var running = true
            var launches = 0
            val coordinator = StartCoordinator(object : SystemSettings { override fun read() = true to Debugging.ENABLED }, object : ServerAccess { override suspend fun alive() = running }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) { launches++ } }, direct)
            val persistence = kotlinx.coroutines.flow.MutableStateFlow(fr.sygix.tsuyu.persistence.PersistenceUi())
            val model = HomeViewModel(coordinator, persistence = persistence)
            store.put("home", model)
            model.resume(); advanceUntilIdle()
            model.act(Action.LANGUAGE); advanceUntilIdle()
            assertEquals("action-language", model.ui.value.focus)
            running = false
            model.died()
            persistence.value = fr.sygix.tsuyu.persistence.PersistenceUi(outcome = fr.sygix.tsuyu.persistence.RestartOutcome.LAUNCH)
            model.resume(); advanceUntilIdle()
            assertEquals("action-language", model.ui.value.focus)
            assertEquals(0, launches)
            for (phase in Phase.entries) {
                assertTrue(HomeViewModel.present(StartState(phase), Screen.HOME, "action-main").controls.any { it.action == Action.LANGUAGE })
            }
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun returnActionAppearsOnlyForASharedCallerOnceTheServiceAndPreparationAreSettled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var running = true
            val coordinator = StartCoordinator(object : SystemSettings { override fun read() = true to Debugging.ENABLED }, object : ServerAccess { override suspend fun alive() = running }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) = Unit }, direct)
            val persistence = kotlinx.coroutines.flow.MutableStateFlow(fr.sygix.tsuyu.persistence.PersistenceUi())
            val model = HomeViewModel(coordinator, persistence = persistence)
            store.put("home", model)
            model.caller("SygixOs"); model.resume(); advanceUntilIdle()
            assertFalse(model.ui.value.controls.any { it.id == "action-return" })
            persistence.value = fr.sygix.tsuyu.persistence.PersistenceUi(enabled = true, loading = false, enabledKnown = true, error = true)
            advanceUntilIdle()
            assertFalse(model.ui.value.controls.any { it.id == "action-return" })
            persistence.value = fr.sygix.tsuyu.persistence.PersistenceUi(enabled = true, loading = false, enabledKnown = true, outcome = fr.sygix.tsuyu.persistence.RestartOutcome.LAUNCH)
            advanceUntilIdle()
            assertFalse(model.ui.value.controls.any { it.id == "action-return" })
            persistence.value = fr.sygix.tsuyu.persistence.PersistenceUi(enabled = true, loading = false, enabledKnown = true, uid = fr.sygix.tsuyu.ipc.Preparation.READY, pkg = fr.sygix.tsuyu.ipc.Preparation.READY)
            advanceUntilIdle()
            assertEquals("action-return", model.ui.value.focus)
            assertEquals(listOf<Any>("SygixOs"), model.ui.value.controls.first().args)
            model.act(Action.APPS); advanceUntilIdle()
            assertEquals("action-apps", model.ui.value.focus)
            model.caller(null); advanceUntilIdle()
            assertFalse(model.ui.value.controls.any { it.id == "action-return" })
            model.caller("SygixOs"); running = false; model.resume(); advanceUntilIdle()
            assertFalse(model.ui.value.controls.any { it.id == "action-return" })
            assertFalse(HomeViewModel.present(StartState(Phase.RUNNING), Screen.DETAILS, "action-main", "SygixOs").controls.any { it.id == "action-return" })
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun persistenceEntryRestoresTheActualInitiatingControl() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val coordinator = StartCoordinator(object : SystemSettings { override fun read() = true to Debugging.ENABLED }, object : ServerAccess { override suspend fun alive() = false }, object : ServerLauncher { override suspend fun launch(progress: (Phase) -> Unit) = Unit }, direct)
            val model = HomeViewModel(coordinator)
            store.put("home", model)
            model.persistenceEntry(true); model.resume(); advanceUntilIdle()
            assertEquals("action-main", model.ui.value.focus)
            model.persistenceEntry(false); model.resume(); advanceUntilIdle()
            assertEquals("action-persistence", model.ui.value.focus)
        } finally { store.clear(); Dispatchers.resetMain() }
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
    @Test fun queuedSetupResumePreservesFailureAndDoesNotAutomaticallyRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var debugging = Debugging.DISABLED
            var launches = 0
            val gate = CompletableDeferred<Unit>()
            val coordinator = StartCoordinator(object : SystemSettings {
                override fun read() = true to debugging
            }, object : ServerAccess {
                override suspend fun alive() = false
            }, object : ServerLauncher {
                override suspend fun launch(progress: (Phase) -> Unit) {
                    launches++; gate.await(); throw StartException(Failure.AUTHENTICATION)
                }
            }, direct)
            val model = HomeViewModel(coordinator)
            store.put("home", model)
            model.resume(); runCurrent()
            model.act(Action.ENABLE); runCurrent()
            debugging = Debugging.ENABLED
            model.resume(); runCurrent()
            model.resume(); runCurrent()
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(1, launches)
            assertEquals(Phase.ERROR, coordinator.state.value.phase)
            assertEquals(Failure.AUTHENTICATION, coordinator.state.value.failure)
            assertEquals(Action.START, model.ui.value.controls.first().action)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
