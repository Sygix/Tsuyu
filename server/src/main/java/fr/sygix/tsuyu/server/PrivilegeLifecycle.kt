/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

class PrivilegeLifecycle {
    private val locks = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.locks.ReentrantLock>()
    private fun lock(uid: Int) = locks.computeIfAbsent(uid) { java.util.concurrent.locks.ReentrantLock() }
    fun <T> withUid(uid: Int, block: () -> T): T {
        val lock = lock(uid)
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }
    fun tryWithUid(uid: Int, block: () -> Unit): Boolean {
        val lock = lock(uid)
        if (!lock.tryLock()) return false
        try { block(); return true } finally { lock.unlock() }
    }
}
