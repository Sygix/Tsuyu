/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.net.LocalSocket
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.DataInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal class SessionProcess(
    private val supervisor: Process,
    private val connection: LocalSocket,
    input: DataInputStream,
    val targetPid: Int,
    private val group: Int,
    private val signal: (Int) -> Unit = ::killGroup,
) : Process() {
    private val result = CompletableFuture<Int>()
    private val stopping = AtomicBoolean()
    private var closed = false

    init {
        Thread {
            try {
                check(input.readInt() == ProcessProtocol.EXITED) { "Process completion unverified" }
                result.complete(input.readInt())
            } catch (failure: Exception) {
                if (stopping.get() && supervisor.waitFor(5, TimeUnit.SECONDS)) result.complete(supervisor.exitValue())
                else result.completeExceptionally(failure)
            }
        }.apply { isDaemon = true; start() }
    }

    override fun getInputStream() = supervisor.inputStream
    override fun getErrorStream() = supervisor.errorStream
    override fun getOutputStream() = supervisor.outputStream
    override fun waitFor(): Int = completed { result.get() }
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = try {
        completed { result.get(timeout, unit) }
        true
    } catch (timeout: TimeoutException) { false }
    override fun exitValue(): Int {
        if (!result.isDone) throw IllegalThreadStateException("Process still running")
        return completed { result.get() }
    }
    override fun isAlive(): Boolean = !result.isDone
    override fun destroy() = terminate()
    override fun destroyForcibly(): Process = apply { terminate() }

    @Synchronized
    fun terminate() {
        if (closed) return
        stopping.set(true)
        if (supervisor.isAlive) signal(group)
        check(supervisor.waitFor(5, TimeUnit.SECONDS)) { "Process group termination unverified" }
        result.complete(supervisor.exitValue())
        connection.close()
        closed = true
    }

    private fun <T> completed(block: () -> T): T = try { block() }
    catch (failure: ExecutionException) { throw IllegalStateException("Process completion unverified", failure.cause) }

    companion object {
        fun killGroup(pid: Int) {
            require(pid > 0)
            try { Os.kill(-pid, OsConstants.SIGKILL) }
            catch (failure: ErrnoException) { if (failure.errno != OsConstants.ESRCH) throw failure }
        }
    }
}
