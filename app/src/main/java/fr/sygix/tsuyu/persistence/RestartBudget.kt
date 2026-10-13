/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

data class RestartRecord(val event: String = "", val started: Long = 0, val attempts: Int = 0, val outcome: String = "NONE")
object RestartBudget {
    fun reserve(previous: RestartRecord, event: String, now: Long): RestartRecord? {
        require(event.isNotBlank() && now >= 0)
        val record = if (previous.event == event) previous else RestartRecord(event, now)
        if (record.attempts >= 3 || remaining(record, now) <= 0 || record.outcome in setOf("RUNNING", "SETUP", "AUTHENTICATION", "KEY", "UNSUPPORTED", "UNKNOWN", "EXHAUSTED")) return null
        return record.copy(attempts = record.attempts + 1, outcome = "ATTEMPT")
    }
    fun remaining(record: RestartRecord, now: Long): Long = (300000 - (now - record.started).coerceAtLeast(0)).coerceAtLeast(0)
}
