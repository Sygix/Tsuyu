/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33, 34], qualifiers = "w960dp-h540dp-xhdpi")
class LanguageActivityNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<LanguageActivity>()

    @Test fun backFinishesTheLanguageActivityWithoutChangingItsConfirmedChoice() = runBlocking {
        val gateway = AndroidAppLanguageGateway(compose.activity.applicationContext)
        val before = gateway.current()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("screen-language").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("screen-language").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrue(compose.activity.isFinishing)
        assertEquals(before, gateway.current())
    }
}
