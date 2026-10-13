/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

object LaunchCommand {
    fun create(path: String, token: String, generation: String): String {
        require(path.startsWith("/data/app/") && path.endsWith("/base.apk") && !path.contains('\u0000') && !path.contains('\n'))
        require(token.matches(Regex("[a-f0-9]{32}")) && generation.matches(Regex("[a-f0-9]{32}")))
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val inner = "trap '' HUP; export CLASSPATH=${quote(path)}; exec /system/bin/app_process /system/bin fr.sygix.tsuyu.server.ServerMain ${quote(path)} $token $generation"
        return "umask 077; setsid sh -c ${quote(inner)} </dev/null >/data/local/tmp/tsuyu-start.log 2>&1 & echo TSUYU_LAUNCHED"
    }
}
