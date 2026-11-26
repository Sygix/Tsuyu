/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import fr.sygix.tsuyu.access.AccessScreen
import fr.sygix.tsuyu.access.AccessUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], qualifiers = "de-w960dp-h540dp-xhdpi")
class LocalizedRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun anErrorStateAndALabelQuotedInFrenchInTheSpecsRenderInGerman() {
        val state = mutableStateOf(AccessUi(loading = false, permission = true))
        compose.setContent { AccessScreen(state.value, {}, {}, {}, {}) }
        compose.onNodeWithTag("access-deny").assertIsFocused().assertTextEquals("Ablehnen")
        compose.runOnIdle { state.value = AccessUi(loading = false, error = true) }
        compose.onNodeWithTag("access-body").assertTextEquals("Die Anfrage ist abgelaufen oder der Dienst ist nicht verfügbar. Öffne Tsuyu, prüfe den Dienst und versuche es erneut. Ein unvollständiger Entzug muss wiederholt werden.")
        compose.onNodeWithTag("access-retry").assertTextEquals("Erneut versuchen")
    }
}
