/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.update

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fr.sygix.tsuyu.R
import fr.sygix.tsuyu.ui.Action
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class UpdateScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun press(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }
    private val offer = Release("v0.3.0", "0.3.0", 300099, false, "https://example.org/a.apk", 4, "c".repeat(64), "https://example.org/v0.3.0")
    private val available = UpdateUi(installed = "0.2.0", offer = offer, checkedAt = 1, result = UpdateController.AVAILABLE, loaded = true)

    @Test fun offeredUpdateTakesInitialFocusAndTheColumnDoesNotWrap() {
        val actions = mutableListOf<Action>()
        compose.setContent { UpdateScreen(UpdateViewModel.present(available, true, null), actions::add) }
        compose.onNodeWithTag("update-install").assertIsFocused().assertTextEquals("Mettre à jour vers 0.3.0")
        compose.onNodeWithTag("update-notes").assertExists()
        press("update-install", Key.DirectionUp)
        press("update-install", Key.DirectionLeft)
        press("update-install", Key.DirectionRight)
        compose.onNodeWithTag("update-install").assertIsFocused()
        for ((from, to) in listOf("update-install" to "update-check", "update-check" to "update-auto", "update-auto" to "update-prereleases", "update-prereleases" to "update-back")) {
            press(from, Key.DirectionDown)
            compose.onNodeWithTag(to).assertIsFocused()
        }
        press("update-back", Key.DirectionDown)
        compose.onNodeWithTag("update-back").assertIsFocused()
        press("update-back", Key.DirectionCenter)
        assertEquals(listOf(Action.BACK), actions)
    }

    @Test fun runningWorkKeepsUnavailableActionsOutOfFocusAndDisappearingOfferFallsBackToCheck() {
        val state = mutableStateOf(UpdateViewModel.present(available.copy(phase = UpdatePhase.DOWNLOADING), true, "update-install"))
        compose.setContent { UpdateScreen(state.value) {} }
        compose.onNodeWithTag("update-back").assertIsFocused()
        compose.onNodeWithTag("update-install").assertIsNotEnabled()
        compose.onNodeWithTag("operation-progress").assertExists()
        press("update-back", Key.DirectionUp)
        compose.onNodeWithTag("update-prereleases").assertIsFocused()
        press("update-prereleases", Key.DirectionUp)
        compose.onNodeWithTag("update-auto").assertIsFocused()
        press("update-auto", Key.DirectionUp)
        compose.onNodeWithTag("update-auto").assertIsFocused()
        compose.runOnIdle { state.value = UpdateViewModel.present(available.copy(offer = null, result = UpdateController.UP_TO_DATE), true, "update-install") }
        compose.onNodeWithTag("update-check").assertIsFocused()
        compose.onNodeWithTag("state-title").assertTextEquals("Tsuyu est à jour")
    }

    @Test fun togglesKeepFocusAndStoppedServiceDisablesInstallation() {
        val state = mutableStateOf(UpdateViewModel.present(available.copy(offer = null, result = null), true, "update-auto"))
        compose.setContent { UpdateScreen(state.value) {} }
        compose.onNodeWithTag("update-auto").assertIsFocused().assertTextEquals("Mises à jour automatiques : Désactivées")
        compose.runOnIdle { state.value = UpdateViewModel.present(available.copy(offer = null, auto = true), true, "update-auto") }
        compose.onNodeWithTag("update-auto").assertIsFocused().assertTextEquals("Mises à jour automatiques : Activées")
        compose.runOnIdle { state.value = UpdateViewModel.present(available, false, null) }
        compose.onNodeWithTag("update-check").assertIsFocused()
        compose.onNodeWithTag("update-install").assertIsNotEnabled()
        compose.onNodeWithTag("state-body").assertTextEquals("Démarrez Tsuyu pour installer la mise à jour.")
    }

    @Test fun presentationNamesEachOutcome() {
        assertEquals(R.string.update_waiting_title, UpdateViewModel.present(available.copy(phase = UpdatePhase.WAITING), true, null).title)
        val failed = UpdateViewModel.present(available.copy(result = "DIGEST"), true, null)
        assertEquals(R.string.update_failed_title, failed.title)
        assertEquals(R.string.update_cause_digest, failed.body)
        assertTrue(failed.attention)
        val offline = UpdateViewModel.present(UpdateUi(installed = "0.2.0", result = "NETWORK"), true, null)
        assertEquals(R.string.update_check_failed_title, offline.title)
        assertEquals("update-check", offline.focus)
        assertEquals(R.string.update_never_body, UpdateViewModel.present(UpdateUi(installed = "0.2.0"), true, null).body)
        assertFalse(UpdateViewModel.present(available.copy(phase = UpdatePhase.INSTALLING), true, null).controls.any { it.id == "update-auto" && it.enabled })
    }
}
