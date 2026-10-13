/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class Deadline : AutoCloseable {
    private val executor = ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS, SynchronousQueue())
    fun <T> call(block: () -> T): T {
        val future = executor.submit<T> { block() }
        try { return future.get(5, TimeUnit.SECONDS) }
        finally { if (!future.isDone) future.cancel(true) }
    }
    override fun close() { executor.shutdownNow() }
}
