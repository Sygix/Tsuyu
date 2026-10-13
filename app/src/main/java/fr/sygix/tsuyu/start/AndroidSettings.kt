/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

class AndroidSettings(private val context: Context, private val readGlobal: (String) -> Int = { Settings.Global.getInt(context.contentResolver, it) }) : SystemSettings {
    override fun read(): Pair<Boolean, Debugging> {
        val developer = runCatching { readGlobal(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1 }.getOrDefault(false)
        val debugging = runCatching {
            if (readGlobal(Settings.Global.ADB_ENABLED) == 1) Debugging.ENABLED else Debugging.DISABLED
        }.getOrDefault(Debugging.UNKNOWN)
        return developer to debugging
    }
    fun observe(changed: () -> Unit): AutoCloseable {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = changed()
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, observer)
        return AutoCloseable { context.contentResolver.unregisterContentObserver(observer) }
    }
}
