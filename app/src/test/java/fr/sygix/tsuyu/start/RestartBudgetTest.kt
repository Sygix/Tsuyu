/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import fr.sygix.tsuyu.persistence.*
import org.junit.Assert.*
import org.junit.Test

class RestartBudgetTest {
    @Test fun reschedulingAndProcessDeathDoNotResetAttemptsOrDeadline() {
        val first = requireNotNull(RestartBudget.reserve(RestartRecord(), "boot:1", 20000))
        assertEquals(1, first.attempts)
        val second = requireNotNull(RestartBudget.reserve(first.copy(outcome = "ENDPOINT"), "boot:1", 60000))
        assertEquals(20000L, second.started)
        assertEquals(2, second.attempts)
        assertEquals(260000L, RestartBudget.remaining(second, 60000))
        val third = requireNotNull(RestartBudget.reserve(second.copy(outcome = "ENDPOINT"), "boot:1", 90000))
        assertNull(RestartBudget.reserve(third.copy(outcome = "ENDPOINT"), "boot:1", 100000))
        assertNull(RestartBudget.reserve(first.copy(outcome = "ENDPOINT"), "boot:1", 320000))
        assertEquals(1, RestartBudget.reserve(third, "boot:2", 10000)?.attempts)
    }
    @Test fun successAndPermanentSetupFailuresStopAutomaticRetry() {
        for (outcome in listOf("RUNNING", "SETUP", "AUTHENTICATION", "KEY", "UNSUPPORTED", "UNKNOWN")) {
            val record = RestartRecord("boot:1", 1000, 1, outcome)
            assertNull(RestartBudget.reserve(record, "boot:1", 2000))
        }
    }
}
