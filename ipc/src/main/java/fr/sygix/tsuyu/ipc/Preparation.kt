/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ipc

enum class Preparation {
    UNKNOWN, READY, DENIED, UNSUPPORTED, FAILED, TIMEOUT;

    companion object {
        fun decode(value: String?): Preparation = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
