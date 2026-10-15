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
class RestartRunner(private val store: PersistenceStorage, private val coordinator: StartCoordinator, private val settings: SystemSettings, private val clock: () -> Long, private val io: BoundedIo = BoundedIo()) {
    suspend fun run(event: String): RestartResult {
        var record: RestartRecord? = null
        try {
            val saved = withTimeout(5000) { store.readEvent(event) }
            if (!saved.enabled) return RestartResult.SUCCESS
            record = withTimeout(5000) { store.reserve(event, clock()) }
            val attempt = record ?: run {
                if (saved.record.outcome == RestartOutcome.RUNNING) {
                    val debugging = io.call(5000) { settings.read().second }
                    if (debugging != Debugging.ENABLED) {
                        withTimeout(5000) { store.complete(saved.record, if (debugging == Debugging.DISABLED) RestartOutcome.SETUP else RestartOutcome.UNKNOWN) }
                        return RestartResult.FAILURE
                    }
                    coordinator.refresh()
                    if (coordinator.state.value.phase == Phase.RUNNING) return RestartResult.SUCCESS
                    withTimeout(5000) { store.complete(saved.record, RestartOutcome.LAUNCH) }
                    return RestartResult.FAILURE
                }
                if (saved.record.event == event && saved.record.outcome.retryable) withTimeout(5000) { store.complete(saved.record, RestartOutcome.EXHAUSTED) }
                return RestartResult.FAILURE
            }
            val debugging = io.call(5000) { settings.read().second }
            if (debugging != Debugging.ENABLED) {
                withTimeout(5000) { store.complete(attempt, if (debugging == Debugging.DISABLED) RestartOutcome.SETUP else RestartOutcome.UNKNOWN) }
                return RestartResult.FAILURE
            }
            var authorizing = false
            val outcome = try {
                withTimeout(RestartBudget.remaining(attempt, clock()).coerceAtMost(165000)) {
                    coroutineScope {
                        val observer = launch { coordinator.state.collect { if (it.phase == Phase.AUTHORIZING) authorizing = true } }
                        try {
                            coordinator.start()
                            coordinator.refresh()
                        } finally { observer.cancel() }
                    }
                    val state = coordinator.state.value
                    when {
                        state.phase == Phase.RUNNING -> RestartOutcome.RUNNING
                        state.debugging == Debugging.DISABLED -> RestartOutcome.SETUP
                        state.debugging == Debugging.UNKNOWN -> RestartOutcome.UNKNOWN
                        authorizing && state.failure == Failure.TIMEOUT -> RestartOutcome.AUTHENTICATION
                        else -> failureOutcome(state.failure)
                    }
                }
            } catch (timeout: TimeoutCancellationException) { if (authorizing) RestartOutcome.AUTHENTICATION else RestartOutcome.TIMEOUT }
            withTimeout(5000) { store.complete(attempt, outcome) }
            if (outcome == RestartOutcome.RUNNING) return RestartResult.SUCCESS
            if (RestartBudget.reserve(attempt.copy(outcome = outcome), event, clock()) == null) {
                if (outcome.retryable) withTimeout(5000) { store.complete(attempt, RestartOutcome.EXHAUSTED) }
                return RestartResult.FAILURE
            }
            return RestartResult.RETRY
        } catch (timeout: TimeoutCancellationException) {
            record?.let { withTimeout(5000) { store.complete(it, RestartOutcome.TIMEOUT) } }
            return RestartResult.FAILURE
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            android.util.Log.e("Tsuyu", "Background restart failed", failure)
            record?.let { withTimeout(5000) { store.complete(it, RestartOutcome.UNKNOWN) } }
            return RestartResult.FAILURE
        }
    }
    private fun failureOutcome(failure: Failure?): RestartOutcome = when (failure) {
        Failure.SETTINGS -> RestartOutcome.SETTINGS
        Failure.ENDPOINT -> RestartOutcome.ENDPOINT
        Failure.UNSUPPORTED -> RestartOutcome.UNSUPPORTED
        Failure.KEY -> RestartOutcome.KEY
        Failure.AUTHENTICATION -> RestartOutcome.AUTHENTICATION
        Failure.TIMEOUT -> RestartOutcome.TIMEOUT
        Failure.BUSY -> RestartOutcome.BUSY
        Failure.LAUNCH, null -> RestartOutcome.LAUNCH
    }
}
