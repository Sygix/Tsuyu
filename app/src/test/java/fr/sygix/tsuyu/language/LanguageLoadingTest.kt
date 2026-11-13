/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LanguageLoadingTest {
    @Test fun uncertainWriteRequiresRefreshBeforeConfirmingOrChangingTheChoice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var actual = LanguageState(AppLanguage.FRENCH)
            var writes = 0
            val model = LanguageViewModel(object : AppLanguageGateway {
                override suspend fun current() = actual
                override suspend fun select(language: AppLanguage?) {
                    writes++
                    actual = actual.copy(selected = language)
                    throw LanguageWriteUncertain()
                }
            })
            model.refresh()
            advanceUntilIdle()
            model.select(AppLanguage.GERMAN)
            advanceUntilIdle()
            assertTrue(model.ui.value.uncertain)
            assertTrue(model.ui.value.error)
            model.select(AppLanguage.SPANISH)
            advanceUntilIdle()
            assertEquals(1, writes)
            model.refresh()
            advanceUntilIdle()
            assertFalse(model.ui.value.uncertain)
            assertFalse(model.ui.value.error)
            assertEquals(AppLanguage.GERMAN, model.ui.value.language?.selected)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun pendingLoadExposesNoUnconfirmedChoiceAndTimeoutOffersRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var timeout = true
            val model = LanguageViewModel(object : AppLanguageGateway {
                override suspend fun current(): LanguageState {
                    if (timeout) withTimeout(100) { awaitCancellation() }
                    return LanguageState(AppLanguage.ITALIAN)
                }
                override suspend fun select(language: AppLanguage?) = Unit
            })
            model.refresh()
            assertTrue(model.ui.value.loading)
            assertNull(model.ui.value.language)
            advanceUntilIdle()
            assertTrue(model.ui.value.error)
            assertFalse(model.ui.value.loading)
            timeout = false
            model.refresh()
            advanceUntilIdle()
            assertFalse(model.ui.value.error)
            assertEquals(AppLanguage.ITALIAN, model.ui.value.language?.selected)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun pendingSaveKeepsConfirmedChoiceAndRejectsConcurrentSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var saved = LanguageState(AppLanguage.FRENCH)
            val gate = CompletableDeferred<Unit>()
            var writes = 0
            val model = LanguageViewModel(object : AppLanguageGateway {
                override suspend fun current() = saved
                override suspend fun select(language: AppLanguage?) {
                    writes++
                    gate.await()
                    saved = saved.copy(selected = language)
                }
            })
            model.refresh()
            advanceUntilIdle()
            model.select(AppLanguage.GERMAN)
            runCurrent()
            model.select(AppLanguage.SPANISH)
            assertTrue(model.ui.value.loading)
            assertEquals(AppLanguage.FRENCH, model.ui.value.language?.selected)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, writes)
            assertEquals(AppLanguage.GERMAN, model.ui.value.language?.selected)
        } finally { Dispatchers.resetMain() }
    }
}
