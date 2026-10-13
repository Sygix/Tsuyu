/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import fr.sygix.tsuyu.ipc.ITsuyuServer
import fr.sygix.tsuyu.start.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

data class PersistenceUi(val enabled: Boolean = false, val uid: String = "UNKNOWN", val pkg: String = "UNKNOWN", val expiry: Long? = null, val outcome: String = "NONE", val loading: Boolean = true, val error: Boolean = false, val setup: Boolean = false) {
    val ready get() = uid == "READY" && pkg == "READY"
    val unsupported get() = uid == "UNSUPPORTED" && pkg == "UNSUPPORTED"
}
class PersistenceController(private val store: PersistenceStorage, private val coordinator: StartCoordinator, private val server: () -> ITsuyuServer?, private val io: BoundedIo = BoundedIo()) {
    private val mutable = MutableStateFlow(PersistenceUi())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    suspend fun refresh() = operation { inspect() }
    suspend fun enable() = operation {
        coordinator.start()
        if (coordinator.state.value.phase != Phase.RUNNING) {
            mutable.value = mutable.value.copy(loading = false, error = true, setup = coordinator.state.value.debugging != Debugging.ENABLED)
            return@operation
        }
        val snapshot = io.call(35000) { requireNotNull(server()).preparePersistence() }
        if (snapshot.getBoolean("schedulable")) withTimeout(5000) { store.enable(); store.recovered() }
        update(snapshot)
    }
    suspend fun disableExpiry() = operation {
        val before = io.call(16000) { requireNotNull(server()).persistence }
        update(before)
        if (before.containsKey("expiry") && before.getLong("expiry") == 0L) return@operation
        val snapshot = io.call(25000) { requireNotNull(server()).disableKeyExpiry() }
        update(snapshot)
        if (!snapshot.containsKey("expiry") || snapshot.getLong("expiry") != 0L) error("Expiry change unverified; refresh before retry")
    }
    private suspend fun inspect() {
        coordinator.refresh()
        val saved = withTimeout(5000) { store.read() }
        mutable.value = mutable.value.copy(enabled = saved.enabled, outcome = saved.record.outcome, setup = coordinator.state.value.debugging != Debugging.ENABLED)
        val binder = server()
        if (binder == null) { mutable.value = mutable.value.copy(uid = "UNKNOWN", pkg = "UNKNOWN", expiry = null, loading = false); return }
        update(io.call(16000) { binder.persistence })
    }
    private suspend fun update(snapshot: android.os.Bundle) {
        val saved = withTimeout(5000) { store.read() }
        mutable.value = PersistenceUi(saved.enabled, snapshot.getString("uid") ?: "UNKNOWN", snapshot.getString("package") ?: "UNKNOWN", if (snapshot.containsKey("expiry")) snapshot.getLong("expiry") else null, saved.record.outcome, loading = false)
    }
    private suspend fun operation(block: suspend () -> Unit) = mutex.withLock {
        mutable.value = mutable.value.copy(loading = true, error = false)
        try { block() }
        catch (timeout: TimeoutCancellationException) { android.util.Log.e("Tsuyu", "Persistence operation timed out", timeout); mutable.value = mutable.value.copy(loading = false, error = true, uid = "UNKNOWN", pkg = "UNKNOWN", expiry = null) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.e("Tsuyu", "Persistence operation failed", failure); mutable.value = mutable.value.copy(loading = false, error = true, uid = "UNKNOWN", pkg = "UNKNOWN", expiry = null) }
    }
}
