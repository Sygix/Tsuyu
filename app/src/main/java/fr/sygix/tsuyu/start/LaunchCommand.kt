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
        val log = "/data/local/tmp/tsuyu-start.log"
        val inner = "trap '' HUP; export CLASSPATH=${quote(path)}; echo TSUYU_DETACHED; exec /system/bin/app_process /system/bin fr.sygix.tsuyu.server.ServerMain ${quote(path)} $token $generation"
        return "umask 077; : >$log || exit 1; setsid sh -c ${quote(inner)} </dev/null >>$log 2>&1 & " +
            "attempt=0; while ! grep -qx TSUYU_READY $log; do " +
            "if grep -qx TSUYU_FAILED $log; then echo TSUYU_FAILED; exit 1; fi; " +
            "if [ \"\$attempt\" -ge 250 ]; then echo TSUYU_TIMEOUT; exit 1; fi; " +
            "sleep 0.1; attempt=\$((attempt + 1)); done; echo TSUYU_LAUNCHED"
    }
}
