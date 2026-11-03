/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.persistence

import fr.sygix.tsuyu.start.BoundedIo
import fr.sygix.tsuyu.start.Debugging
import fr.sygix.tsuyu.start.Failure
import fr.sygix.tsuyu.start.StartException
import fr.sygix.tsuyu.start.SystemSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class KeyRefreshOutcome { NONE, CURRENT, AUTHORIZATION_REQUIRED, FAILED }
enum class KeyRefreshResult { SUCCESS, RETRY, FAILURE }

class KeyRefreshRunner(
    private val store: PersistenceStorage,
    private val settings: SystemSettings,
    private val refresh: suspend () -> Unit,
    private val io: BoundedIo = BoundedIo(),
    private val report: (Exception) -> Unit = {},
) {
    suspend fun run(): KeyRefreshResult {
        val outcome = try {
            withTimeout(30_000) {
                if (!store.read().enabled) return@withTimeout null
                when (io.call(5000) { settings.read().second }) {
                    Debugging.DISABLED -> return@withTimeout null
                    Debugging.UNKNOWN -> throw StartException(Failure.SETTINGS)
                    Debugging.ENABLED -> refresh()
                }
                KeyRefreshOutcome.CURRENT
            }
        } catch (timeout: TimeoutCancellationException) {
            report(timeout)
            KeyRefreshOutcome.FAILED
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            report(failure)
            if (failure is StartException && failure.failure == Failure.AUTHENTICATION) KeyRefreshOutcome.AUTHORIZATION_REQUIRED
            else KeyRefreshOutcome.FAILED
        }
        if (outcome == null) return KeyRefreshResult.SUCCESS
        withTimeout(5000) { store.keyRefreshed(outcome) }
        return when (outcome) {
            KeyRefreshOutcome.CURRENT -> KeyRefreshResult.SUCCESS
            KeyRefreshOutcome.AUTHORIZATION_REQUIRED -> KeyRefreshResult.FAILURE
            else -> KeyRefreshResult.RETRY
        }
    }
}
