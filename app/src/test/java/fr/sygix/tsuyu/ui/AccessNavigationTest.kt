/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fr.sygix.tsuyu.access.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class AccessNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun press(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }
    @Test fun refusalIsInitialAndOnlyHorizontalDirectionsReachAllow() {
        val decisions = mutableListOf<Boolean>()
        compose.setContent { AccessScreen(AccessUi(loading = false), true, decisions::add, {}, {}, {}) }
        compose.onNodeWithTag("access-deny").assertIsFocused()
        press("access-deny", Key.DirectionDown)
        compose.onNodeWithTag("access-deny").assertIsFocused()
        press("access-deny", Key.DirectionRight)
        compose.onNodeWithTag("access-allow").assertIsFocused()
        press("access-allow", Key.DirectionRight)
        compose.onNodeWithTag("access-allow").assertIsFocused()
        press("access-allow", Key.DirectionLeft)
        press("access-deny", Key.DirectionCenter)
        assertEquals(listOf(false), decisions)
    }
    @Test fun emptyListHasReturnAndFallbackNeverTargetsRemovedRow() {
        compose.setContent { AccessScreen(AccessUi(loading = false), false, {}, {}, {}, {}) }
        compose.onNodeWithTag("access-back").assertIsFocused()
        assertEquals(3, AccessViewModel.fallback(2, listOf(3, 4)))
        assertNull(AccessViewModel.fallback(2, emptyList()))
    }
}
