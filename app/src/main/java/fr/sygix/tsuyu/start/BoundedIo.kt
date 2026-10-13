/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

open class BoundedIo {
    private val workers = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue())
    open suspend fun <T> call(timeout: Long, cancel: () -> Unit = {}, block: () -> T): T = withTimeout(timeout) {
        suspendCancellableCoroutine { continuation ->
            val task = try { workers.submit {
                continuation.resumeWith(runCatching(block))
            } } catch (error: Exception) {
                continuation.resumeWith(Result.failure(StartException(Failure.BUSY, error)))
                null
            }
            continuation.invokeOnCancellation { cancel(); task?.cancel(true) }
        }
    }
}
