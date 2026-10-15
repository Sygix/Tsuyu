/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import org.junit.Assert.*
import org.junit.Test

class CleanupTest {
    @Test fun partialFailureStillStopsEveryOtherCapability() {
        val stopped = mutableListOf<String>()
        val failure = assertThrows(IllegalStateException::class.java) {
            Cleanup.run(listOf({ error("child did not stop") }, { stopped += "first" }, { stopped += "second" }))
        }
        assertEquals(listOf("first", "second"), stopped)
        assertEquals(1, failure.suppressed.size)
    }
}
