/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.os.Binder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClientDeliveryTest {
    @Test fun transientEnumerationAndExhaustedDeliveryCanRecoverForTheSameLivePid() {
        val failed = CountDownLatch(1)
        val exhausted = CountDownLatch(3)
        val success = CountDownLatch(1)
        var time = 0L
        var enumerateFails = true
        var sendFails = true
        Deadline().use { deadline ->
            ClientDelivery(Binder(), 0, deadline, {
                if (enumerateFails) failed.countDown() else exhausted.countDown()
            }, { success.countDown() }, enumerate = {
                check(!enumerateFails)
                listOf("client")
            }, send = { _, _ -> check(!sendFails) }, clock = { time }).use { delivery ->
                delivery.processStarted(123, 10001)
                assertTrue(failed.await(3, TimeUnit.SECONDS))
                enumerateFails = false
                time = 6000
                delivery.processStarted(123, 10001)
                assertTrue(exhausted.await(4, TimeUnit.SECONDS))
                sendFails = false
                time = 12000
                delivery.processStarted(123, 10001)
                assertTrue(success.await(3, TimeUnit.SECONDS))
            }
        }
    }
    @Test fun stateChangesDeliverOncePerPidAndDeathAllowsRedelivery() {
        val scans = AtomicInteger()
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val sends = AtomicInteger()
        Deadline().use { deadline ->
            ClientDelivery(Binder(), 0, deadline, { fail("Unexpected failure") }, {
                if (sends.get() == 1) first.countDown() else second.countDown()
            }, enumerate = {
                scans.incrementAndGet(); listOf("client")
            }, send = { _, _ -> sends.incrementAndGet(); Unit }).use { delivery ->
                repeat(100) { delivery.processStarted(123, 10001) }
                assertTrue(first.await(3, TimeUnit.SECONDS))
                delivery.processDied(123)
                delivery.processStarted(123, 10001)
                assertTrue(second.await(3, TimeUnit.SECONDS))
                assertEquals(2, scans.get())
                assertEquals(2, sends.get())
            }
        }
    }
    @Test fun missingClientProviderDoesNotMarkTheServiceAsBroken() {
        val failures = AtomicInteger()
        val healthy = CountDownLatch(1)
        val missing = CountDownLatch(1)
        val reported = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        Deadline().use { deadline ->
            ClientDelivery(Binder(), 0, deadline, { failures.incrementAndGet() }, { healthy.countDown() },
                enumerate = { listOf("missing", "healthy") },
                send = { name, _ -> if (name == "missing") throw ProviderUnavailable(name) },
                reportClient = { name, delivered -> reported[name] = delivered; if (name == "missing") missing.countDown() },
            ).use { delivery ->
                delivery.processStarted(123, 10001)
                assertTrue(healthy.await(3, TimeUnit.SECONDS))
                assertTrue(missing.await(3, TimeUnit.SECONDS))
            }
            assertEquals(0, failures.get())
            assertEquals(false, reported["missing"])
            assertEquals(true, reported["healthy"])
        }
    }
}
