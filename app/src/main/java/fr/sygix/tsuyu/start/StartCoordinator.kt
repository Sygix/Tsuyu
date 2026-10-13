/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import java.util.concurrent.atomic.AtomicBoolean

class StartCoordinator(private val settings: SystemSettings, private val server: ServerAccess, private val launcher: ServerLauncher, private val io: BoundedIo = BoundedIo(), private val report: (Exception) -> Unit = {}) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(StartState())
    val state = mutable.asStateFlow()
    suspend fun refresh() {
        mutex.lock()
        try { inspect() }
        catch (timeout: TimeoutCancellationException) { report(timeout); mutable.value = mutable.value.copy(phase = Phase.ERROR, failure = Failure.TIMEOUT) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure); mutable.value = mutable.value.copy(phase = Phase.ERROR, failure = (failure as? StartException)?.failure ?: Failure.SETTINGS) }
        finally { mutex.unlock() }
    }
    private suspend fun inspect(): Boolean {
        val alive = withTimeout(5000) { server.alive() }
        val result = io.call(5000) { settings.read() }
        val previous = mutable.value
        if (!alive && previous.phase == Phase.ERROR && previous.developer == result.first && previous.debugging == result.second) return false
        mutable.value = StartState(if (alive) Phase.RUNNING else if (result.second != Debugging.ENABLED) Phase.SETUP else Phase.STOPPED, result.first, result.second)
        return alive
    }
    suspend fun start() {
        if (!mutex.tryLock()) return
        val active = AtomicBoolean(true)
        val owner = currentCoroutineContext().job
        try {
            if (inspect() || mutable.value.debugging != Debugging.ENABLED) return
            mutable.value = mutable.value.copy(phase = Phase.CONNECTING, failure = null)
            launcher.launch { phase -> if (active.get() && owner.isActive) mutable.value = mutable.value.copy(phase = phase, failure = null) }
            check(withTimeout(5000) { server.alive() }) { "Server handshake missing" }
            mutable.value = mutable.value.copy(phase = Phase.RUNNING)
        } catch (timeout: TimeoutCancellationException) {
            active.set(false)
            report(timeout)
            mutable.value = mutable.value.copy(phase = Phase.ERROR, failure = Failure.TIMEOUT)
        } catch (cancelled: CancellationException) {
            active.set(false)
            mutable.value = mutable.value.copy(phase = Phase.STOPPED, failure = null)
            throw cancelled
        } catch (failure: Exception) {
            active.set(false)
            report(failure)
            mutable.value = mutable.value.copy(phase = Phase.ERROR, failure = (failure as? StartException)?.failure ?: Failure.LAUNCH)
        } finally { active.set(false); mutex.unlock() }
    }
    fun died() { if (mutable.value.phase == Phase.RUNNING) mutable.value = mutable.value.copy(phase = Phase.STOPPED) }
    fun settingsFailed() { mutable.value = mutable.value.copy(phase = Phase.ERROR, failure = Failure.SETTINGS) }
}
