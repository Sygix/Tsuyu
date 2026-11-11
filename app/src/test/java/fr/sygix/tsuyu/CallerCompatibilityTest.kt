/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class CallerCompatibilityTest {
    @Test fun olderPlatformsNeverResolveForgedCallerIdentity() {
        val intent = Intent().apply {
            putExtra("caller", "client")
            putExtra("android.intent.extra.REFERRER_NAME", "android-app://client")
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://client"))
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).get()
        val resolve = MainActivity::class.java.getDeclaredMethod("resolveCaller").apply { isAccessible = true }
        assertNull(resolve.invoke(activity))
    }
}
