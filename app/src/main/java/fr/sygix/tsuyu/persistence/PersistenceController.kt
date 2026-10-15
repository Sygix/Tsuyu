/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import fr.sygix.tsuyu.ipc.ITsuyuServer
import fr.sygix.tsuyu.ipc.Preparation
import fr.sygix.tsuyu.start.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import android.os.Bundle
import android.util.Log

enum class StartRoute { START, DISCLOSURE, ERROR }
data class PersistenceUi(
    val enabled: Boolean = false,
    val uid: Preparation = Preparation.UNKNOWN,
    val pkg: Preparation = Preparation.UNKNOWN,
    val expiry: Long? = null,
    val outcome: RestartOutcome = RestartOutcome.NONE,
    val loading: Boolean = true,
    val error: Boolean = false,
    val setup: Boolean = false,
    val enabledKnown: Boolean = false,
    val settingsError: Boolean = false,
) {
    val ready get() = uid == Preparation.READY && pkg == Preparation.READY
    val unsupported get() = uid == Preparation.UNSUPPORTED && pkg == Preparation.UNSUPPORTED
}
class PersistenceController(
    private val store: PersistenceStorage,
    private val coordinator: StartCoordinator,
    private val server: () -> ITsuyuServer?,
    private val io: BoundedIo = BoundedIo(),
) {
    private val mutable = MutableStateFlow(PersistenceUi())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    suspend fun startRoute(): StartRoute = mutex.withLock {
        try {
            val saved = withTimeout(STORAGE_TIMEOUT_MS) { store.read() }
            mutable.value = mutable.value.copy(enabled = saved.enabled, enabledKnown = true)
            if (saved.enabled) StartRoute.START else StartRoute.DISCLOSURE
        } catch (cancelled: CancellationException) {
            if (cancelled !is TimeoutCancellationException) throw cancelled
            mutable.value = mutable.value.copy(loading = false, error = true, enabledKnown = false)
            StartRoute.ERROR
        } catch (failure: Exception) {
            Log.e("Tsuyu", "Persistence configuration unavailable", failure)
            mutable.value = mutable.value.copy(loading = false, error = true, enabledKnown = false)
            StartRoute.ERROR
        }
    }
    fun settingsFailed() {
        mutable.value = mutable.value.copy(loading = false, error = true, settingsError = true)
    }
    suspend fun refresh() = operation { inspect() }
    suspend fun enable() = operation {
        coordinator.start()
        if (coordinator.state.value.phase != Phase.RUNNING) {
            mutable.value = mutable.value.copy(loading = false, error = true, setup = coordinator.state.value.debugging != Debugging.ENABLED)
            return@operation
        }
        val snapshot = io.call(PREPARE_TIMEOUT_MS) { requireNotNull(server()).preparePersistence() }
        if (snapshot.getBoolean("schedulable")) {
            withTimeout(STORAGE_TIMEOUT_MS) {
                store.enable()
                store.recovered()
            }
        }
        update(snapshot)
        if (!snapshot.getBoolean("schedulable")) mutable.value = mutable.value.copy(error = true)
    }
    suspend fun disableExpiry() = operation {
        val before = io.call(STATUS_TIMEOUT_MS) { requireNotNull(server()).persistence }
        update(before)
        if (before.containsKey("expiry") && before.getLong("expiry") == 0L) return@operation
        val snapshot = io.call(EXPIRY_TIMEOUT_MS) { requireNotNull(server()).disableKeyExpiry() }
        update(snapshot)
        if (!snapshot.containsKey("expiry") || snapshot.getLong("expiry") != 0L) error("Expiry change unverified; refresh before retry")
    }
    private suspend fun inspect() {
        coordinator.refresh()
        val saved = withTimeout(STORAGE_TIMEOUT_MS) { store.read() }
        mutable.value = mutable.value.copy(
            enabled = saved.enabled, enabledKnown = true, outcome = saved.record.outcome,
            setup = coordinator.state.value.debugging != Debugging.ENABLED,
        )
        val binder = server()
        if (binder == null) {
            mutable.value = mutable.value.copy(uid = Preparation.UNKNOWN, pkg = Preparation.UNKNOWN, expiry = null, loading = false)
            return
        }
        update(io.call(STATUS_TIMEOUT_MS) { binder.persistence })
    }
    private suspend fun update(snapshot: Bundle) {
        val saved = withTimeout(STORAGE_TIMEOUT_MS) { store.read() }
        mutable.value = PersistenceUi(
            enabled = saved.enabled,
            uid = Preparation.decode(snapshot.getString("uid")),
            pkg = Preparation.decode(snapshot.getString("package")),
            expiry = if (snapshot.containsKey("expiry")) snapshot.getLong("expiry") else null,
            outcome = saved.record.outcome,
            loading = false,
            enabledKnown = true,
        )
    }
    private suspend fun operation(block: suspend () -> Unit) = mutex.withLock {
        mutable.value = mutable.value.copy(loading = true, error = false)
        try { block() }
        catch (timeout: TimeoutCancellationException) { failed("Persistence operation timed out", timeout) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { failed("Persistence operation failed", failure) }
    }
    private fun failed(message: String, failure: Exception) {
        Log.e("Tsuyu", message, failure)
        mutable.value = mutable.value.copy(
            loading = false, error = true, uid = Preparation.UNKNOWN, pkg = Preparation.UNKNOWN, expiry = null,
        )
    }
    companion object {
        private const val STORAGE_TIMEOUT_MS = 5_000L
        private const val STATUS_TIMEOUT_MS = 16_000L
        private const val PREPARE_TIMEOUT_MS = 45_000L
        private const val EXPIRY_TIMEOUT_MS = 25_000L
    }
}
