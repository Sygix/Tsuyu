/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fr.sygix.tsuyu.start.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class NavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun press(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }
    @Test fun homeFocusMovesWithoutWrappingAndCenterActivates() {
        val actions = mutableListOf<Action>()
        compose.setContent { HomeScreen(HomeViewModel.present(StartState(Phase.STOPPED), Screen.HOME, "action-main"), actions::add) }
        compose.onNodeWithTag("action-main").assertIsFocused()
        press("action-main", Key.DirectionUp)
        press("action-main", Key.DirectionLeft)
        press("action-main", Key.DirectionRight)
        compose.onNodeWithTag("action-main").assertIsFocused()
        press("action-main", Key.DirectionDown)
        compose.onNodeWithTag("action-help").assertIsFocused()
        press("action-help", Key.DirectionDown)
        compose.onNodeWithTag("action-help").assertIsFocused()
        press("action-help", Key.DirectionCenter)
        assertEquals(listOf(Action.HELP), actions)
    }
    @Test fun helpReturnAndRemovedFocusedActionRestoreValidFocus() {
        val state = mutableStateOf(HomeViewModel.present(StartState(Phase.STOPPED), Screen.HELP, "action-help"))
        compose.setContent { HomeScreen(state.value) {} }
        compose.onNodeWithTag("action-back").assertIsFocused()
        compose.runOnIdle { state.value = HomeViewModel.present(StartState(Phase.STOPPED), Screen.HOME, "action-help") }
        compose.onNodeWithTag("action-help").assertIsFocused()
        compose.runOnIdle { state.value = HomeViewModel.present(StartState(Phase.AUTHORIZING), Screen.SETUP, "action-help") }
        compose.onNodeWithTag("action-cancel").assertIsFocused()
        compose.runOnIdle { state.value = HomeViewModel.present(StartState(Phase.RUNNING), Screen.HOME, "action-cancel") }
        compose.onNodeWithTag("action-main").assertIsFocused()
    }
    @Test fun setupAndSettingsReturnHaveAReachableAction() {
        val state = mutableStateOf(HomeViewModel.present(StartState(Phase.SETUP, true, Debugging.DISABLED), Screen.SETUP, "action-main"))
        compose.setContent { HomeScreen(state.value) {} }
        compose.onNodeWithTag("action-settings").assertIsFocused()
        press("action-settings", Key.DirectionDown)
        compose.onNodeWithTag("action-settings").assertIsFocused()
        compose.runOnIdle { state.value = HomeViewModel.present(StartState(Phase.STOPPED, true, Debugging.ENABLED), Screen.SETUP, "action-settings") }
        compose.onNodeWithTag("action-main").assertIsFocused()
        assertEquals("action-main", FocusPolicy.restore("removed", listOf("action-main", "action-help")))
    }
}
