/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu

import android.app.Application
import fr.sygix.tsuyu.distribution.onApplicationStart

class TsuyuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        onApplicationStart(this)
    }
}
