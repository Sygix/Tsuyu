/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationTest {
    @Test fun missingOrForbiddenSettingsReportsRecoveryWithoutCrashing() {
        var reported = 0
        SettingsNavigation.open({ throw android.content.ActivityNotFoundException() }, { reported++ })
        SettingsNavigation.open({ throw SecurityException() }, { reported++ })
        assertEquals(2, reported)
    }
}
