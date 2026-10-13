/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

object RestartLedger {
    fun reserve(records: Map<String, RestartRecord>, event: String, now: Long): RestartRecord? = RestartBudget.reserve(records[event] ?: RestartRecord(), event, now)
}
