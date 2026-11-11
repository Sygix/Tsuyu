/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import android.util.Log
import fr.sygix.tsuyu.start.BoundedIo
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal class LanguageWriteUncertain : IllegalStateException("Reconcile the outstanding language change before choosing again")

internal class LanguageWriteBarrier(
    private val timeout: Long = 5_000,
    private val writer: Executor = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1)).apply {
        allowCoreThreadTimeOut(true)
    },
) {
    private val io = BoundedIo()
    private val mutex = Mutex()
    private var pending: CompletableDeferred<Result<Unit>>? = null

    suspend fun <T> read(block: () -> T): T = mutex.withLock {
        val outstanding = pending
        if (outstanding != null && !outstanding.isCompleted) throw LanguageWriteUncertain()
        outstanding?.await()?.exceptionOrNull()?.let { Log.e("Tsuyu", "Reconciling failed platform locale write", it) }
        val result = io.call(timeout, block = block)
        pending = null
        result
    }

    suspend fun write(block: () -> Unit) = mutex.withLock {
        if (pending != null) throw LanguageWriteUncertain()
        val result = CompletableDeferred<Result<Unit>>()
        pending = result
        try {
            writer.execute { result.complete(runCatching(block)) }
        } catch (failure: Exception) {
            result.complete(Result.failure(failure))
        }
        try { withTimeout(timeout) { result.await() }.getOrThrow() }
        catch (timeout: TimeoutCancellationException) { throw LanguageWriteUncertain() }
    }
}
