/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fr.sygix.tsuyu.persistence.*
import fr.sygix.tsuyu.ipc.Preparation
import fr.sygix.tsuyu.start.Phase
import fr.sygix.tsuyu.start.StartState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PersistenceNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun press(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }
    @Test fun automaticAuthorizationOffersCancelAndRequestAgainWithStableFocus() {
        val actions = mutableListOf<Action>()
        val state = mutableStateOf(PersistenceViewModel.present(PersistenceUi(loading = true), false, "persistence-enable", StartState(Phase.AUTHORIZING)))
        compose.setContent { PersistenceScreen(state.value, actions::add) }
        compose.onNodeWithTag("persistence-cancel").assertIsFocused()
        press("persistence-cancel", Key.DirectionUp)
        compose.onNodeWithTag("persistence-cancel").assertIsFocused()
        press("persistence-cancel", Key.DirectionDown)
        compose.onNodeWithTag("persistence-reauthorize").assertIsFocused()
        press("persistence-reauthorize", Key.DirectionDown)
        compose.onNodeWithTag("persistence-reauthorize").assertIsFocused()
        press("persistence-reauthorize", Key.DirectionCenter)
        assertEquals(listOf(Action.REAUTHORIZE), actions)
        compose.runOnIdle { state.value = PersistenceViewModel.present(PersistenceUi(loading = false, error = true), false, "persistence-cancel", StartState(Phase.STOPPED)) }
        compose.onNodeWithTag("persistence-enable").assertIsFocused()
        compose.onNodeWithTag("persistence-cancel").assertDoesNotExist()
        compose.onNodeWithTag("persistence-reauthorize").assertDoesNotExist()
    }
    @Test fun authorizationWarningFocusesRenewalAndReachesDisableWithoutWrapping() {
        val actions = mutableListOf<Action>()
        val warned = PersistenceUi(enabled = true, enabledKnown = true, loading = false, keyRefresh = KeyRefreshOutcome.AUTHORIZATION_REQUIRED)
        val state = mutableStateOf(PersistenceViewModel.present(warned, false, null, StartState(Phase.RUNNING)))
        compose.setContent { PersistenceScreen(state.value, actions::add) }
        compose.onNodeWithTag("persistence-renew").assertIsFocused()
        press("persistence-renew", Key.DirectionUp)
        compose.onNodeWithTag("persistence-renew").assertIsFocused()
        press("persistence-renew", Key.DirectionCenter)
        press("persistence-renew", Key.DirectionDown)
        compose.onNodeWithTag("persistence-enable").assertIsFocused()
        press("persistence-enable", Key.DirectionDown)
        press("persistence-expiry", Key.DirectionDown)
        press("persistence-refresh", Key.DirectionDown)
        compose.onNodeWithTag("persistence-disable").assertIsFocused()
        press("persistence-disable", Key.DirectionCenter)
        assertEquals(listOf(Action.RENEW, Action.DISABLE_PERSISTENCE), actions)
        compose.runOnIdle { state.value = PersistenceViewModel.present(warned.copy(loading = true), false, "persistence-disable", StartState(Phase.RUNNING)) }
        compose.onNodeWithTag("persistence-disable").assertIsFocused()
        compose.runOnIdle { state.value = PersistenceViewModel.present(PersistenceUi(enabledKnown = true, loading = false), false, null, StartState(Phase.RUNNING)) }
        compose.onNodeWithTag("persistence-enable").assertIsFocused()
        compose.onNodeWithTag("persistence-disable").assertDoesNotExist()
    }
    @Test fun optionalChoiceInitiallyKeepsPolicyAndDoesNotWrap() {
        val actions = mutableListOf<Action>()
        val state = mutableStateOf(PersistenceViewModel.present(PersistenceUi(loading = false, expiry = 604800000), true, "expiry-disable"))
        compose.setContent { PersistenceScreen(state.value, actions::add) }
        compose.onNodeWithTag("expiry-keep").assertIsFocused()
        press("expiry-keep", Key.DirectionUp)
        compose.onNodeWithTag("expiry-keep").assertIsFocused()
        press("expiry-keep", Key.DirectionCenter)
        assertEquals(listOf(Action.BACK), actions)
        press("expiry-keep", Key.DirectionDown)
        compose.onNodeWithTag("expiry-disable").assertIsFocused()
        press("expiry-disable", Key.DirectionCenter)
        assertEquals(listOf(Action.BACK, Action.DISABLE_EXPIRY), actions)
        compose.runOnIdle { state.value = PersistenceViewModel.present(PersistenceUi(loading = false, expiry = 0), true, "expiry-disable") }
        compose.onNodeWithTag("expiry-keep").assertIsFocused()
        compose.onNodeWithTag("expiry-disable").assertDoesNotExist()
    }
    @Test fun setupReturnRestoresItsEntryAndStatusViewingHasNoAction() {
        val ui = PersistenceViewModel.present(PersistenceUi(enabled = true, uid = Preparation.READY, pkg = Preparation.READY, loading = false), false, "persistence-expiry")
        val actions = mutableListOf<Action>()
        compose.setContent { PersistenceScreen(ui, actions::add) }
        compose.onNodeWithTag("persistence-expiry").assertIsFocused()
        assertTrue(actions.isEmpty())
        compose.onNodeWithTag("persistence-modes").assertExists()
    }
}
