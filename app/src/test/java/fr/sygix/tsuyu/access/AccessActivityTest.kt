/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.access

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessActivityTest {
    @Test fun permissionScreenOpensWhenTheFirmwareRefusesOverlayHiding() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activity = Robolectric.buildActivity(AccessActivity::class.java, Intent(context, AccessActivity::class.java).putExtra("request", "pending")).setup().get()
        assertFalse(activity.isFinishing)
    }
}
