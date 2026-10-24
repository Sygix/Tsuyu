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
@Config(sdk = [34], qualifiers = "fr-w960dp-h540dp-xhdpi")
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
        compose.onNodeWithTag("action-apps").assertIsFocused()
        press("action-apps", Key.DirectionDown)
        compose.onNodeWithTag("action-updates").assertIsFocused()
        press("action-updates", Key.DirectionDown)
        compose.onNodeWithTag("action-persistence").assertIsFocused()
        press("action-persistence", Key.DirectionDown)
        compose.onNodeWithTag("action-help").assertIsFocused()
        press("action-help", Key.DirectionDown)
        compose.onNodeWithTag("action-language").assertIsFocused()
        press("action-language", Key.DirectionDown)
        compose.onNodeWithTag("action-language").assertIsFocused()
        press("action-language", Key.DirectionCenter)
        assertEquals(listOf(Action.LANGUAGE), actions)
    }
    @Test fun returnActionTakesInitialFocusAndFallsBackWhenRemoved() {
        val actions = mutableListOf<Action>()
        val state = mutableStateOf(HomeViewModel.present(StartState(Phase.RUNNING), Screen.HOME, "action-main", "SygixOs"))
        compose.setContent { HomeScreen(state.value, actions::add) }
        compose.onNodeWithTag("action-return").assertIsFocused().assertTextEquals("Revenir à SygixOs")
        press("action-return", Key.DirectionUp)
        compose.onNodeWithTag("action-return").assertIsFocused()
        press("action-return", Key.DirectionDown)
        compose.onNodeWithTag("action-main").assertIsFocused()
        press("action-main", Key.DirectionUp)
        press("action-return", Key.DirectionCenter)
        assertEquals(listOf(Action.RETURN), actions)
        compose.runOnIdle { state.value = HomeViewModel.present(StartState(Phase.RUNNING), Screen.HOME, "action-return") }
        compose.onNodeWithTag("action-main").assertIsFocused()
    }
    @Test fun informationStaysLeftOfTheActionColumnInsideTheSafeArea() {
        compose.setContent { HomeScreen(HomeViewModel.present(StartState(Phase.LAUNCHING), Screen.HOME, "action-main"), {}) }
        val root = compose.onRoot().getBoundsInRoot()
        val body = compose.onNodeWithTag("state-body").getBoundsInRoot()
        val brand = compose.onNodeWithTag("brand-signature").getBoundsInRoot()
        compose.onNodeWithTag("operation-progress").assertExists()
        assertTrue(compose.onNodeWithTag("brand-signature").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Focused).not())
        for (tag in listOf("action-cancel", "action-apps", "action-updates", "action-persistence", "action-help")) {
            val action = compose.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue(tag, action.left > body.right && action.left > brand.right)
            assertTrue(tag, action.right <= root.right - (root.right - root.left) * 0.05f + androidx.compose.ui.unit.Dp.Hairline)
            assertTrue(tag, action.top >= root.top && action.bottom <= root.bottom)
        }
        assertTrue(HomeViewModel.present(StartState(Phase.AUTHORIZING), Screen.SETUP, "action-main").busy)
        assertFalse(HomeViewModel.present(StartState(Phase.RUNNING), Screen.HOME, "action-main").busy)
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
