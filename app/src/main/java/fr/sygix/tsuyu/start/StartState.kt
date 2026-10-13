/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

enum class Debugging { ENABLED, DISABLED, UNKNOWN }
enum class Phase { CHECKING, SETUP, STOPPED, CONNECTING, AUTHORIZING, LAUNCHING, RUNNING, ERROR }
enum class Failure { SETTINGS, ENDPOINT, UNSUPPORTED, KEY, AUTHENTICATION, TIMEOUT, LAUNCH, BUSY }
data class StartState(val phase: Phase = Phase.CHECKING, val developer: Boolean = false, val debugging: Debugging = Debugging.UNKNOWN, val failure: Failure? = null)
class StartException(val failure: Failure, cause: Throwable? = null) : Exception(failure.name, cause)
interface SystemSettings {
    fun read(): Pair<Boolean, Debugging>
}
interface ServerAccess {
    suspend fun alive(): Boolean
}
interface ServerLauncher {
    suspend fun launch(progress: (Phase) -> Unit)
}
