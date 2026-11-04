/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

open class BoundedIo {
    private val workers = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(2)).apply { allowCoreThreadTimeOut(true) }
    open suspend fun <T> call(timeout: Long, cancel: () -> Unit = {}, block: () -> T): T = withTimeout(timeout) {
        suspendCancellableCoroutine { continuation ->
            val task = try { workers.submit {
                continuation.resumeWith(runCatching(block))
            } } catch (error: Exception) {
                continuation.resumeWith(Result.failure(StartException(Failure.BUSY, error)))
                null
            }
            continuation.invokeOnCancellation {
                try { cancel() }
                finally {
                    task?.cancel(true)
                    if (task is Runnable) workers.remove(task)
                }
            }
        }
    }
}
