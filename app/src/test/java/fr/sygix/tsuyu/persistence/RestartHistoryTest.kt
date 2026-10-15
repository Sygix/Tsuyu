/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import org.junit.Assert.*
import org.junit.Test

class RestartHistoryTest {
    @Test fun alternatingCurrentBootAndUpdateCannotResetEitherBudget() {
        var records = emptyMap<String, RestartRecord>()
        val events = listOf("boot:2", "update:2:99")
        repeat(3) { attempt ->
            events.forEach { event ->
                val record = requireNotNull(RestartBudget.reserve(records, event, attempt * 30000L))
                assertEquals(attempt + 1, record.attempts)
                assertEquals(0L, record.started)
                records = records + (event to record.copy(outcome = RestartOutcome.ENDPOINT))
            }
        }
        events.forEach { assertNull(RestartBudget.reserve(records, it, 100000)); assertNull(RestartBudget.reserve(records, it, 300001)) }
        assertNotNull(RestartBudget.reserve(records, "boot:3", 20000))
    }
}
