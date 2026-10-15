/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

enum class RestartOutcome {
    NONE, ATTEMPT, RUNNING, RECOVERED, SETUP, AUTHENTICATION, KEY, UNSUPPORTED, UNKNOWN,
    EXHAUSTED, ENDPOINT, TIMEOUT, LAUNCH, BUSY, SETTINGS;

    val retryable get() = when (this) {
        ENDPOINT, TIMEOUT, LAUNCH, BUSY, ATTEMPT -> true
        else -> false
    }
    val failed get() = when (this) {
        NONE, RUNNING, RECOVERED, ATTEMPT -> false
        else -> true
    }
    val terminal get() = when (this) {
        RUNNING, RECOVERED, SETUP, AUTHENTICATION, KEY, UNSUPPORTED, UNKNOWN, EXHAUSTED -> true
        else -> false
    }

    companion object {
        fun decode(value: String?): RestartOutcome = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
data class RestartRecord(
    val event: String = "",
    val started: Long = 0,
    val attempts: Int = 0,
    val outcome: RestartOutcome = RestartOutcome.NONE,
)
object RestartBudget {
    private const val MAX_ATTEMPTS = 3
    private const val TOTAL_BUDGET_MS = 300_000L
    fun reserve(records: Map<String, RestartRecord>, event: String, now: Long): RestartRecord? =
        reserve(records[event] ?: RestartRecord(), event, now)
    fun reserve(previous: RestartRecord, event: String, now: Long): RestartRecord? {
        require(event.isNotBlank() && now >= 0)
        val record = if (previous.event == event) previous else RestartRecord(event, now)
        if (record.attempts >= MAX_ATTEMPTS || remaining(record, now) <= 0 || record.outcome.terminal) return null
        return record.copy(attempts = record.attempts + 1, outcome = RestartOutcome.ATTEMPT)
    }
    fun remaining(record: RestartRecord, now: Long): Long = (TOTAL_BUDGET_MS - (now - record.started).coerceAtLeast(0)).coerceAtLeast(0)
}
