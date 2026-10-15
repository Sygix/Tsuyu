/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ui

object SettingsNavigation {
    fun open(launch: () -> Unit, unavailable: () -> Unit) {
        try { launch() } catch (failure: Exception) { unavailable() }
    }
}
