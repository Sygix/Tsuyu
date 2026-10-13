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

class PrivilegeLifecycleTest {
    @Test fun revokeSerializesWithAuthorizedChildPublicationForBothChildTypes() {
        for (type in listOf("remote", "service")) {
            val gate = PrivilegeLifecycle()
            val executor = Executors.newFixedThreadPool(2)
            val authorized = CountDownLatch(1)
            val resume = CountDownLatch(1)
            var child: String? = null
            try {
                val launch = executor.submit { gate.withUid(110001) { authorized.countDown(); check(resume.await(5, TimeUnit.SECONDS)); child = type } }
                check(authorized.await(5, TimeUnit.SECONDS))
                val interleave = executor.submit<Boolean> { gate.tryWithUid(110001) { child = null } }
                assertFalse(interleave.get(5, TimeUnit.SECONDS))
                resume.countDown()
                launch.get(5, TimeUnit.SECONDS)
                val revoke = executor.submit { gate.withUid(110001) { child = null } }
                revoke.get(5, TimeUnit.SECONDS)
                assertNull(child)
            } finally { resume.countDown(); executor.shutdownNow() }
        }
    }
}
