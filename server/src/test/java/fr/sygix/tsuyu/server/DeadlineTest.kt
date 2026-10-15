/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DeadlineTest {
    @Test fun concurrentBinderCallsAreQueuedInsteadOfRejected() {
        val callers = Executors.newFixedThreadPool(24)
        val started = CountDownLatch(4)
        val release = CountDownLatch(1)
        Deadline().use { deadline ->
            try {
                val results = (1..24).map { callers.submit<Int> { deadline.call { started.countDown(); check(release.await(3, TimeUnit.SECONDS)); 13 } } }
                assertTrue(started.await(3, TimeUnit.SECONDS))
                release.countDown()
                results.forEach { assertEquals(13, it.get(4, TimeUnit.SECONDS)) }
            } finally { release.countDown(); callers.shutdownNow() }
        }
    }
    @Test fun blockedDeliveryDoesNotConsumeStorageCapacity() {
        val callers = Executors.newFixedThreadPool(16)
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        Deadline().use { deadline ->
            try {
                repeat(16) { callers.submit { deadline.call(Deadline.Use.DELIVERY) { entered.countDown(); release.await() } } }
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                assertEquals("available", deadline.call(Deadline.Use.STORAGE) { "available" })
            } finally { release.countDown(); callers.shutdownNow() }
        }
    }
}
