/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSettingsTest {
    @Test fun visibleSettingsDistinguishEnabledAndDisabledDebugging() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(true to Debugging.ENABLED, AndroidSettings(context) { 1 }.read())
        assertEquals(false to Debugging.DISABLED, AndroidSettings(context) { 0 }.read())
    }
    @Test fun deniedAndMissingSettingsRemainUnknownRatherThanDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (failure in listOf(SecurityException("Denied"), Settings.SettingNotFoundException("Missing"))) {
            val settings = AndroidSettings(context) { throw failure }
            assertEquals(false to Debugging.UNKNOWN, settings.read())
        }
    }
}
