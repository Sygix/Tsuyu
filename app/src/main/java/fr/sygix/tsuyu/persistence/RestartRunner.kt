/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import fr.sygix.tsuyu.start.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class RestartResult { SUCCESS, FAILURE, RETRY }
class RestartRunner(private val store: PersistenceStorage, private val coordinator: StartCoordinator, private val settings: SystemSettings, private val clock: () -> Long, private val cleanupUpdate: suspend () -> Unit = {}, private val io: BoundedIo = BoundedIo()) {
    suspend fun run(event: String): RestartResult {
        var record: RestartRecord? = null
        try {
            val saved = withTimeout(5000) { store.readEvent(event) }
            if (!saved.enabled) return RestartResult.SUCCESS
            record = withTimeout(5000) { store.reserve(event, clock()) }
            val attempt = record ?: run {
                if (saved.record.outcome == "RUNNING") {
                    val debugging = io.call(5000) { settings.read().second }
                    if (debugging != Debugging.ENABLED) {
                        withTimeout(5000) { store.complete(saved.record, if (debugging == Debugging.DISABLED) "SETUP" else "UNKNOWN") }
                        return RestartResult.FAILURE
                    }
                    coordinator.refresh()
                    if (coordinator.state.value.phase == Phase.RUNNING) return RestartResult.SUCCESS
                    withTimeout(5000) { store.complete(saved.record, "LAUNCH") }
                    return RestartResult.FAILURE
                }
                if (saved.record.event == event && saved.record.outcome in setOf("ENDPOINT", "TIMEOUT", "LAUNCH", "BUSY", "ATTEMPT")) withTimeout(5000) { store.complete(saved.record, "EXHAUSTED") }
                return RestartResult.FAILURE
            }
            val debugging = io.call(5000) { settings.read().second }
            if (debugging != Debugging.ENABLED) {
                withTimeout(5000) { store.complete(attempt, if (debugging == Debugging.DISABLED) "SETUP" else "UNKNOWN") }
                return RestartResult.FAILURE
            }
            var authorizing = false
            val outcome = try {
                withTimeout(RestartBudget.remaining(attempt, clock()).coerceAtMost(165000)) {
                    coroutineScope {
                        val observer = launch { coordinator.state.collect { if (it.phase == Phase.AUTHORIZING) authorizing = true } }
                        try {
                            if (event.startsWith("update:")) cleanupUpdate()
                            coordinator.start()
                            coordinator.refresh()
                        } finally { observer.cancel() }
                    }
                    val state = coordinator.state.value
                    when {
                        state.phase == Phase.RUNNING -> "RUNNING"
                        state.debugging == Debugging.DISABLED -> "SETUP"
                        state.debugging == Debugging.UNKNOWN -> "UNKNOWN"
                        authorizing && state.failure == Failure.TIMEOUT -> "AUTHENTICATION"
                        else -> state.failure?.name ?: "LAUNCH"
                    }
                }
            } catch (timeout: TimeoutCancellationException) { if (authorizing) "AUTHENTICATION" else "TIMEOUT" }
            withTimeout(5000) { store.complete(attempt, outcome) }
            if (outcome == "RUNNING") return RestartResult.SUCCESS
            if (RestartBudget.reserve(attempt.copy(outcome = outcome), event, clock()) == null) {
                if (outcome in setOf("ENDPOINT", "TIMEOUT", "LAUNCH", "BUSY")) withTimeout(5000) { store.complete(attempt, "EXHAUSTED") }
                return RestartResult.FAILURE
            }
            return RestartResult.RETRY
        } catch (timeout: TimeoutCancellationException) {
            record?.let { withTimeout(5000) { store.complete(it, "TIMEOUT") } }
            return RestartResult.FAILURE
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            android.util.Log.e("Tsuyu", "Background restart failed", failure)
            record?.let { withTimeout(5000) { store.complete(it, "UNKNOWN") } }
            return RestartResult.FAILURE
        }
    }
}
