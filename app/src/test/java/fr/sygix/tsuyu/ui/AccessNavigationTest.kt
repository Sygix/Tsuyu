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
@Config(sdk = [34], qualifiers = "fr-w960dp-h540dp-xhdpi")
class AccessNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun press(tag: String, key: Key) = compose.onNodeWithTag(tag).performKeyInput { keyDown(key); keyUp(key) }
    @Test fun refusalIsInitialAndOnlyHorizontalDirectionsReachAllow() {
        val decisions = mutableListOf<Boolean>()
        compose.setContent { AccessScreen(AccessUi(loading = false, permission = true), decisions::add, {}, {}, {}) }
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
        compose.setContent { AccessScreen(AccessUi(loading = false), {}, {}, {}, {}) }
        compose.onNodeWithTag("access-back").assertIsFocused()
        assertEquals(3, AccessViewModel.fallback(2, listOf(3, 4)))
        assertNull(AccessViewModel.fallback(2, emptyList()))
    }
    @Test fun refusedRowOffersResetAndListFocusFollowsRowsWithoutWrapping() {
        val icon = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val rows = listOf(AccessRow(10001, listOf(AccessApp("Allowed", icon))), AccessRow(10002, listOf(AccessApp("SygixOs", icon)), refused = true))
        val resets = mutableListOf<Int>()
        compose.setContent { AccessScreen(AccessUi(loading = false, rows = rows), {}, {}, {}, {}, resets::add) }
        compose.onNodeWithTag("access-title").assertTextEquals("Accès des applications")
        compose.onNodeWithTag("revoke-10001").assertIsFocused()
        compose.onNodeWithTag("status-reset-10002").assertTextEquals("Accès refusé")
        compose.onNodeWithTag("status-revoke-10001").assertTextEquals("Autorisée")
        press("revoke-10001", Key.DirectionUp)
        press("revoke-10001", Key.DirectionRight)
        compose.onNodeWithTag("revoke-10001").assertIsFocused()
        press("revoke-10001", Key.DirectionDown)
        compose.onNodeWithTag("reset-10002").assertIsFocused().assertTextEquals("Autoriser une nouvelle demande")
        press("reset-10002", Key.DirectionDown)
        compose.onNodeWithTag("access-back").assertIsFocused()
        press("access-back", Key.DirectionDown)
        compose.onNodeWithTag("access-back").assertIsFocused()
        press("access-back", Key.DirectionUp)
        press("reset-10002", Key.DirectionCenter)
        assertEquals(listOf(10002), resets)
    }
    @Test fun longSharedGroupsKeepEveryMemberAndTheirActionReachable() {
        val icon = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val long = "Une application au nom particulièrement long pour un écran de télévision"
        val rows = (1..4).map { index -> AccessRow(10000 + index, listOf(AccessApp("$long $index", icon), AccessApp("Membre $index", icon))) }
        compose.setContent { AccessScreen(AccessUi(loading = false, rows = rows), {}, {}, {}, {}) }
        for (index in 1..4) {
            if (index > 1) press("revoke-${10000 + index - 1}", Key.DirectionDown)
            compose.onNodeWithTag("revoke-${10000 + index}").assertIsFocused().assertIsDisplayed()
            compose.onNodeWithTag("group-revoke-${10000 + index}").assert(hasAnyDescendant(hasText("Membre $index")))
        }
        press("revoke-10004", Key.DirectionDown)
        compose.onNodeWithTag("access-back").assertIsFocused().assertIsDisplayed()
    }
    @Test fun permissionRequestUsesTheCenteredSurface() {
        compose.setContent { AccessScreen(AccessUi(loading = false, permission = true), {}, {}, {}, {}) }
        val root = compose.onRoot().getBoundsInRoot()
        val surface = compose.onNodeWithTag("access-request-surface").getBoundsInRoot()
        assertTrue(surface.left > root.left + (root.right - root.left) * 0.05f)
        assertEquals((root.left + root.right).value / 2, (surface.left + surface.right).value / 2, 2f)
        compose.onNodeWithTag("access-request-surface").assert(hasAnyDescendant(hasTestTag("access-deny")) and hasAnyDescendant(hasTestTag("access-allow")))
    }
    @Test fun eachRevokeActionIdentifiesItsOwnGroup() {
        val icon = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val rows = listOf(AccessRow(10001, listOf(AccessApp("First", icon))), AccessRow(10002, listOf(AccessApp("Second", icon))))
        compose.setContent { AccessScreen(AccessUi(loading = false, rows = rows), {}, {}, {}, {}) }
        compose.onNodeWithTag("group-revoke-10001").assert(hasAnyDescendant(hasText("First")))
        compose.onNodeWithTag("group-revoke-10001").assert(hasAnyDescendant(hasTestTag("revoke-10001")))
        compose.onNodeWithTag("group-revoke-10002").assert(hasAnyDescendant(hasText("Second")) and hasAnyDescendant(hasTestTag("revoke-10002")))
        compose.onNodeWithTag("group-revoke-10001").assert(hasAnyDescendant(hasTestTag("revoke-10002")).not())
    }
    @Test fun permissionTextUsesReadableForegroundOnTheDarkBackground() {
        compose.setContent { AccessScreen(AccessUi(loading = false, permission = true), {}, {}, {}, {}) }
        for ((tag, color) in listOf("access-title" to fr.sygix.tsuyu.ui.theme.TsuyuColors.text, "access-body" to fr.sygix.tsuyu.ui.theme.TsuyuColors.secondary)) {
            compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(layouts))
                assertEquals(color, layouts.single().layoutInput.style.color)
            }
        }
    }
}
