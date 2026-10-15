/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class Deadline : AutoCloseable {
    enum class Use { SYSTEM, IDENTITY, STORAGE, CALLBACK, DELIVERY }
    private val executors = Use.entries.associateWith {
        ThreadPoolExecutor(4, 16, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64)).apply { allowCoreThreadTimeOut(true) }
    }
    fun <T> call(use: Use = Use.SYSTEM, block: () -> T): T {
        val future = try { executors.getValue(use).submit<T> { block() } }
        catch (failure: RejectedExecutionException) {
            throw IllegalStateException("$use capacity exhausted; restart Tsuyu and retry", failure)
        }
        try { return future.get(5, TimeUnit.SECONDS) }
        catch (failure: ExecutionException) { throw (failure.cause as? Exception ?: failure) }
        catch (failure: TimeoutException) {
            throw IllegalStateException("$use timed out; retry or restart Tsuyu", failure)
        }
        finally { if (!future.isDone) future.cancel(true) }
    }
    override fun close() { executors.values.forEach { it.shutdownNow() } }
}
