/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

internal object HiddenApiAccess {
    fun enable() {
        val runtime = Class.forName("dalvik.system.VMRuntime")
        val instance = runtime.getDeclaredMethod("getRuntime").invoke(null)
        runtime.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
            .invoke(instance, arrayOf("L"))
    }
}
