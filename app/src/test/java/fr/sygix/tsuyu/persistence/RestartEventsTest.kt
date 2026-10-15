/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import org.junit.Assert.*
import org.junit.Test

class RestartEventsTest {
    @Test fun newBootSchedulesIndependentlyAndRejectsPreviousBootWork() {
        assertNotEquals(RestartEvents.workName("boot:1"), RestartEvents.workName("boot:2"))
        assertEquals(RestartEvents.workName("boot:2"), RestartEvents.workName("boot:2"))
        assertFalse(RestartEvents.current("boot:1", 2, 99))
        assertTrue(RestartEvents.current("boot:2", 2, 99))
        val interrupted = RestartRecord("boot:1", 200000, 3, RestartOutcome.ATTEMPT)
        val next = requireNotNull(RestartBudget.reserve(interrupted, "boot:2", 20000))
        assertEquals(1, next.attempts)
        assertEquals(20000L, next.started)
        assertFalse(RestartEvents.current("update:1:99", 2, 99))
        assertFalse(RestartEvents.current("update:2:98", 2, 99))
        assertTrue(RestartEvents.current("update:2:99", 2, 99))
    }
}
