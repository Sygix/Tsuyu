/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.server

import android.os.Bundle
import android.os.Build
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29, 30, 31, 34])
class ProviderBridgeTest {
    @Test fun acquisitionFailureIsDistinctFromProviderAbsence() {
        val failure = SecurityException("Denied")
        val bridge = ProviderBridge(0, acquire = { _, _ -> throw failure },
            release = { fail("No provider acquired") },
            invoke = { _, _, _, _ -> fail("Acquisition failed"); null },
        )
        assertSame(failure, assertThrows(SecurityException::class.java) {
            bridge.call("client.shizuku", "sendBinder", Bundle())
        })
    }

    @Test fun platformSignatureCarriesShellIdentityAndPayload() {
        val type = Class.forName("android.content.IContentProvider")
        val extras = Bundle().apply { putString("key", "value") }
        var called = false
        val provider = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, arguments ->
            assertEquals("call", method.name)
            val args = requireNotNull(arguments)
            val identitySize = when {
                Build.VERSION.SDK_INT >= 31 -> 2
                Build.VERSION.SDK_INT >= 30 -> 3
                Build.VERSION.SDK_INT >= 29 -> 2
                else -> 1
            }
            assertEquals(identitySize + 3, args.size)
            if (Build.VERSION.SDK_INT >= 31) {
                assertEquals(2000, args[0].javaClass.getMethod("getUid").invoke(args[0]))
                assertEquals("com.android.shell", args[0].javaClass.getMethod("getPackageName").invoke(args[0]))
            } else assertEquals("com.android.shell", args[0])
            if (Build.VERSION.SDK_INT >= 29) assertEquals("client.shizuku", args[identitySize - 1])
            if (Build.VERSION.SDK_INT == 30) assertNull(args[1])
            assertEquals("sendBinder", args[identitySize])
            assertNull(args[identitySize + 1])
            assertSame(extras, args[identitySize + 2])
            called = true
            Bundle().apply { putBoolean("received", true) }
        }
        assertTrue(ProviderCall.call(provider, "client.shizuku", "sendBinder", extras)!!.getBoolean("received"))
        assertTrue(called)
    }

    @Test fun acquiredProviderIsReleasedAfterSuccessAndFailure() {
        val provider = Any()
        var releases = 0
        var fail = false
        val bridge = ProviderBridge(7,
            acquire = { authority, user -> assertEquals("client.shizuku", authority); assertEquals(7, user); provider },
            release = { assertEquals("client.shizuku", it); releases++ },
            invoke = { actual, authority, method, extras ->
                assertSame(provider, actual)
                assertEquals("client.shizuku", authority)
                assertEquals("sendBinder", method)
                assertEquals("value", extras.getString("key"))
                if (fail) throw IllegalArgumentException("invocation failed")
                Bundle().apply { putBoolean("received", true) }
            },
        )
        val extras = Bundle().apply { putString("key", "value") }
        assertTrue(bridge.call("client.shizuku", "sendBinder", extras)!!.getBoolean("received"))
        assertEquals(1, releases)
        fail = true
        assertThrows(IllegalArgumentException::class.java) { bridge.call("client.shizuku", "sendBinder", extras) }
        assertEquals(2, releases)
    }

    @Test fun missingProviderIsNotInvokedOrReleased() {
        val bridge = ProviderBridge(0, acquire = { _, _ -> null },
            release = { fail("No provider acquired") },
            invoke = { _, _, _, _ -> fail("Missing provider invoked"); null },
        )
        assertThrows(ProviderUnavailable::class.java) { bridge.call("missing", "sendBinder", Bundle()) }
    }
}
