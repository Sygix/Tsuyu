/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.util.concurrent.ConcurrentHashMap

internal class ClientRevocations(
    private val access: AccessAuthority,
    private val resolve: (Int) -> AccessGroup,
    private val cancelRequests: (Int) -> Unit,
    private val stopProcesses: (Int) -> Unit,
    private val stopServices: (Int) -> Unit,
    private val stopClient: (String, Int) -> Unit,
) {
    private val pending = ConcurrentHashMap<Int, AccessGroup>()
    val groups: List<AccessGroup> get() = pending.values.toList()
    fun contains(uid: Int): Boolean = pending.containsKey(uid)

    fun revoke(uid: Int) {
        val group = pending[uid] ?: resolve(uid)
        pending[uid] = group
        Cleanup.run(
            listOf({ cancelRequests(uid) }, { access.clear(uid) }, { stopProcesses(uid) }, { stopServices(uid) }) +
                group.members.keys.map { name -> { stopClient(name, uid / PER_USER_RANGE) } },
        )
        pending.remove(uid, group)
    }

    companion object {
        private const val PER_USER_RANGE = 100_000
    }
}
