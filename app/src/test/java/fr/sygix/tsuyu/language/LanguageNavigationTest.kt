/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33, 34], qualifiers = "w960dp-h540dp-xhdpi")
class LanguageNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun press(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }

    @Test fun uncertainWriteRemovesConfirmationAndFocusesReconciliationUntilRefresh() {
        val state = mutableStateOf(LanguageUi(LanguageState(AppLanguage.FRENCH), error = true, uncertain = true))
        compose.setContent {
            LanguageScreen(state.value, { fail("Uncertain selection must stay disabled") }, {
                state.value = LanguageUi(LanguageState(AppLanguage.GERMAN))
            })
        }
        compose.onNodeWithTag("language-fr").assertIsNotSelected().assertIsNotEnabled()
        compose.onAllNodesWithTag("language-check", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("language-retry").assertIsFocused()
        press("language-retry", Key.DirectionCenter)
        compose.onNodeWithTag("language-de").assertIsSelected().assertIsFocused()
        compose.onNodeWithTag("language-error").assertDoesNotExist()
    }

    @Test fun selectionAndFocusAreIndependentAndCenterAppliesTheFocusedChoice() {
        val state = mutableStateOf(LanguageUi(LanguageState(AppLanguage.FRENCH, AppLanguage.GERMAN)))
        val selections = mutableListOf<AppLanguage?>()
        compose.setContent {
            LanguageScreen(state.value, { selections += it; state.value = LanguageUi(state.value.language!!.copy(selected = it)) }, {})
        }
        compose.onNodeWithTag("language-fr").assertIsFocused().assertIsSelected()
        press("language-fr", Key.DirectionUp)
        compose.onNodeWithTag("language-es").assertIsFocused().assertIsNotSelected()
        compose.onNodeWithTag("language-fr").assertIsSelected()
        press("language-es", Key.DirectionCenter)
        assertEquals(listOf(AppLanguage.SPANISH), selections)
        compose.onNodeWithTag("language-es").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("language-fr").assertIsNotSelected()
        compose.onAllNodesWithTag("language-check", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun systemDefaultIsSelectedEvenWhenEnglishIsTheFallbackAndEdgesDoNotWrap() {
        compose.setContent { LanguageScreen(LanguageUi(LanguageState()), {}, {}) }
        compose.onNodeWithTag("language-system").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("language-en").assertIsNotSelected()
        compose.onNodeWithTag("language-system-detail").assertTextContains("English", substring = true)
        press("language-system", Key.DirectionUp)
        press("language-system", Key.DirectionLeft)
        press("language-system", Key.DirectionRight)
        compose.onNodeWithTag("language-system").assertIsFocused()
        var previous = "system"
        for (language in AppLanguage.entries) {
            press("language-$previous", Key.DirectionDown)
            compose.onNodeWithTag("language-${language.tag}").assertIsFocused().assertIsDisplayed()
            previous = language.tag
        }
        press("language-it", Key.DirectionDown)
        compose.onNodeWithTag("language-it").assertIsFocused()
    }

    @Test fun readFailureOffersARetryAndSuccessfulRetryFocusesTheActualPreference() {
        val state = mutableStateOf(LanguageUi(error = true))
        compose.setContent { LanguageScreen(state.value, {}, { state.value = LanguageUi(LanguageState(AppLanguage.ITALIAN)) }) }
        compose.onNodeWithTag("language-retry").assertIsFocused()
        compose.onNodeWithTag("language-system").assertIsNotEnabled()
        press("language-retry", Key.DirectionCenter)
        compose.onNodeWithTag("language-error").assertDoesNotExist()
        compose.onNodeWithTag("language-it").assertIsFocused().assertIsSelected()
    }

    @Test fun pendingWriteDisablesChoicesAndFailureRestoresConfirmedFocus() {
        val state = mutableStateOf(LanguageUi(LanguageState(AppLanguage.FRENCH)))
        compose.setContent { LanguageScreen(state.value, {}, {}) }
        compose.onNodeWithTag("language-fr").assertIsFocused()
        compose.runOnIdle { state.value = state.value.copy(loading = true) }
        compose.onNodeWithTag("language-fr").assertIsNotEnabled().assertIsSelected()
        compose.onNodeWithTag("language-de").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(loading = false, error = true) }
        compose.onNodeWithTag("language-fr").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("language-error").assertExists()
        press("language-fr", Key.DirectionLeft)
        compose.onNodeWithTag("language-retry").assertIsFocused()
    }
}
