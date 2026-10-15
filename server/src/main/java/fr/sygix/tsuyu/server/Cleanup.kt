/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

internal object Cleanup {
    fun run(steps: List<() -> Unit>) {
        val failures = steps.mapNotNull { step ->
            try { step(); null } catch (failure: Exception) { failure }
        }
        if (failures.isNotEmpty()) {
            throw IllegalStateException("Access revoked; cleanup incomplete. Retry in Tsuyu.").apply {
                failures.forEach(::addSuppressed)
            }
        }
    }
}
