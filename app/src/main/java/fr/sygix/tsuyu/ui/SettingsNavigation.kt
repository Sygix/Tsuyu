/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

import android.content.Intent
import android.provider.Settings

object SettingsNavigation {
    fun intent(developer: Boolean) = Intent(if (developer) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS)
    fun open(launch: () -> Unit, unavailable: () -> Unit) {
        try { launch() } catch (failure: Exception) { unavailable() }
    }
}
