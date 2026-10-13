/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

enum class ServerStatus { ABSENT, PENDING, READY }

class ServerReadiness(private val io: BoundedIo, private val status: () -> ServerStatus) : ServerAccess {
    override suspend fun alive(): Boolean = withTimeout(5000) {
        var observed = io.call(5000, block = status)
        while (observed == ServerStatus.PENDING) {
            delay(100)
            observed = io.call(5000, block = status)
        }
        observed == ServerStatus.READY
    }
}
