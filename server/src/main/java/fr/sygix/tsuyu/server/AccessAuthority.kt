/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

data class AccessGroup(val uid: Int, val members: Map<String, String>) {
    init { require(uid >= 0 && members.isNotEmpty() && members.all { it.key.isNotBlank() && it.value.isNotBlank() }) }
}
data class AccessDecision(val group: AccessGroup, val allowed: Boolean)
interface AccessStorage {
    fun read(): List<AccessDecision>
    fun write(decisions: List<AccessDecision>)
}
class AccessAuthority(private val resolve: (Int) -> AccessGroup, private val storage: AccessStorage) {
    private var saved: List<AccessDecision>? = null
    @Synchronized fun invalidate() { saved = null }
    @Synchronized fun reload() { saved = null; saved = storage.read() }
    private fun read(): List<AccessDecision> = saved ?: storage.read().also { saved = it }
    private fun write(decisions: List<AccessDecision>) {
        saved = null
        storage.write(decisions)
        saved = decisions
    }
    @Synchronized private fun decision(uid: Int): AccessDecision? {
        val group = resolve(uid)
        val decisions = read()
        if (decisions.any { it.group.uid == uid && it.group != group }) {
            write(decisions.filterNot { it.group.uid == uid })
            return null
        }
        return decisions.singleOrNull { it.group == group }
    }
    fun allowed(uid: Int): Boolean = decision(uid)?.allowed == true
    fun rationale(uid: Int): Boolean = decision(uid)?.allowed == false
    @Synchronized fun decide(uid: Int, allowed: Boolean) {
        val group = resolve(uid)
        write(read().filterNot { it.group.uid == uid } + AccessDecision(group, allowed))
    }
    @Synchronized fun decide(expected: AccessGroup, allowed: Boolean) {
        check(resolve(expected.uid) == expected) { "Consent identity changed" }
        write(read().filterNot { it.group.uid == expected.uid } + AccessDecision(expected, allowed))
    }
    @Synchronized fun clear(uid: Int) { write(read().filterNot { it.group.uid == uid }) }
    @Synchronized fun reset(uid: Int): Boolean {
        val group = try { resolve(uid) } catch (missing: Exception) { return false }
        val decisions = read()
        if (decisions.none { it.group == group && !it.allowed }) return false
        write(decisions.filterNot { it.group.uid == uid })
        return true
    }
    @Synchronized fun refused(): List<AccessGroup> = read().filter { decision ->
        !decision.allowed && try { resolve(decision.group.uid) == decision.group } catch (missing: Exception) { false }
    }.map { it.group }
}
