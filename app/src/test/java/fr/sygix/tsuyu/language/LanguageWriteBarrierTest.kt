/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.language

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LanguageWriteBarrierTest {
    @Test fun timedOutNonInterruptibleWriteMustSettleBeforeReadOrAnotherSelection() = runBlocking {
        val writer = Executors.newSingleThreadExecutor()
        val completed = CountDownLatch(1)
        val barrier = LanguageWriteBarrier(timeout = 1_000, writer = Executor { task ->
            writer.execute { task.run(); completed.countDown() }
        })
        val entered = CountDownLatch(1)
        val release = Semaphore(0)
        val actual = AtomicReference(AppLanguage.FRENCH)
        try {
            val first = async(Dispatchers.Default) {
                runCatching {
                    barrier.write {
                        entered.countDown()
                        release.acquireUninterruptibly()
                        actual.set(AppLanguage.GERMAN)
                    }
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(first.await().isFailure)
            assertEquals(AppLanguage.FRENCH, actual.get())
            val uncertainRead = runCatching { barrier.read { actual.get() } }
            assertTrue(uncertainRead.exceptionOrNull() is LanguageWriteUncertain)
            val competingWrite = runCatching { barrier.write { actual.set(AppLanguage.SPANISH) } }
            assertTrue(competingWrite.exceptionOrNull() is LanguageWriteUncertain)
            assertEquals(AppLanguage.FRENCH, actual.get())
            release.release()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(AppLanguage.GERMAN, barrier.read { actual.get() })
            barrier.write { actual.set(AppLanguage.ITALIAN) }
            assertEquals(AppLanguage.ITALIAN, barrier.read { actual.get() })
        } finally {
            release.release()
            writer.shutdownNow()
            assertTrue(writer.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
