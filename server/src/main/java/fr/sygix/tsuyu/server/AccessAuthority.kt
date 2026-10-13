/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

data class AccessGroup(val uid: Int, val members: Map<String, String>) {
    init { require(uid >= 10000 && members.isNotEmpty() && members.all { it.key.isNotBlank() && it.value.isNotBlank() }) }
}
data class AccessDecision(val group: AccessGroup, val allowed: Boolean)
interface AccessStorage {
    fun read(): List<AccessDecision>
    fun write(decisions: List<AccessDecision>)
}
class AccessAuthority(private val resolve: (Int) -> AccessGroup, private val storage: AccessStorage) {
    @Synchronized private fun decision(uid: Int): AccessDecision? {
        val group = resolve(uid)
        val decisions = storage.read()
        if (decisions.any { it.group.uid == uid && it.group != group }) {
            storage.write(decisions.filterNot { it.group.uid == uid })
            return null
        }
        return decisions.singleOrNull { it.group == group }
    }
    fun allowed(uid: Int): Boolean = decision(uid)?.allowed == true
    fun rationale(uid: Int): Boolean = decision(uid)?.allowed == false
    @Synchronized fun decide(uid: Int, allowed: Boolean) {
        val group = resolve(uid)
        storage.write(storage.read().filterNot { it.group.uid == uid } + AccessDecision(group, allowed))
    }
    @Synchronized fun clear(uid: Int) { storage.write(storage.read().filterNot { it.group.uid == uid }) }
}
