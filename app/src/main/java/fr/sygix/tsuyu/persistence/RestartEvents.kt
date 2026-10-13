/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

object RestartEvents {
    fun workName(event: String): String {
        require(event.matches(Regex("boot:[0-9]+|update:[0-9]+:[0-9]+")))
        return "tsuyu-restart:$event"
    }
    fun current(event: String, boot: Int, update: Long): Boolean = event == "boot:$boot" || event == "update:$boot:$update"
}
